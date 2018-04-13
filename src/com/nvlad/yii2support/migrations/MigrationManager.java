package com.nvlad.yii2support.migrations;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.nvlad.yii2support.common.FileUtil;
import com.nvlad.yii2support.common.YiiCommandLineUtil;
import com.nvlad.yii2support.migrations.entities.Migration;
import com.nvlad.yii2support.migrations.util.MigrationUtil;
import com.nvlad.yii2support.utils.Yii2SupportSettings;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MigrationManager {
    private static final Map<Project, MigrationManager> migrationManagerMap = new HashMap<>();

    public static MigrationManager getInstance(Project project) {
        if (!migrationManagerMap.containsKey(project)) {
            migrationManagerMap.put(project, new MigrationManager(project));
        }

        return migrationManagerMap.get(project);
    }

    private final Project myProject;

    private MigrationManager(Project project) {
        myProject = project;
    }

    public Map<String, Collection<Migration>> getMigrations() {
        PhpIndex phpIndex = PhpIndex.getInstance(myProject);
        Collection<PhpClass> migrations = phpIndex.getAllSubclasses("\\yii\\db\\Migration");
        int baseUrlLength = myProject.getBaseDir().getUrl().length();

        Map<String, Collection<Migration>> migrationMap = new HashMap<>();
        for (PhpClass migration : migrations) {
            VirtualFile virtualFile = FileUtil.getVirtualFile(migration.getContainingFile());
            if (virtualFile.getUrl().length() < baseUrlLength) {
                continue;
            }

            String path = virtualFile.getUrl().substring(baseUrlLength + 1);
            int pathLength = path.length();
            path = path.substring(0, pathLength - virtualFile.getName().length() - 1);

            if (!migrationMap.containsKey(path)) {
                migrationMap.put(path, new LinkedList<>());
            }

            migrationMap.get(path).add(new Migration(migration, path));
        }

        return migrationMap;
    }

    private static Pattern historyPattern = Pattern.compile("(\\(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\) m\\d{6}_\\d{6}_[\\w+_-]+)", Pattern.MULTILINE);
    private static Pattern historyEntryPattern = Pattern.compile("\\((\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\) (m\\d{6}_\\d{6}_[\\w+_-]+)");

    @Nullable
    public Map<String, Date> migrateHistory() {
        try {
            Process process = YiiCommandLineUtil.executeCommand(myProject, "migrate/history", "all");
            if (process.waitFor() != 0) {
                return null;
            }
            String error = readStream(process.getErrorStream());
            if (error != null) {
                return null;
            }
            String stream = readStream(process.getInputStream());
            if (stream == null) {
                return null;
            }

            Map<String, Date> result = new HashMap<>();
            if (stream.contains("No migration has been done before.")) {
                return result;
            }

            Matcher matcher = historyPattern.matcher(stream);
            while (matcher.find()) {
                String historyEntry = matcher.group(1);
                Matcher entryMatcher = historyEntryPattern.matcher(historyEntry);
                if (entryMatcher.find()) {
                    result.put(entryMatcher.group(2), MigrationUtil.applyDate(entryMatcher.group(1)));
                }
            }

            return result;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    Pattern migrateUpPattern = Pattern.compile("\\*\\*\\* applied (m\\d{6}_\\d{6}_.+?) \\(time: ");

    public Set<String> migrateUp(String path, int count) {
        try {
            Yii2SupportSettings settings = Yii2SupportSettings.getInstance(myProject);
            LinkedList<String> params = new LinkedList<>();
            params.push(String.valueOf(count));
            if (settings.dbConnection != null) {
                params.push("--db=" + settings.dbConnection);
            }
            if (settings.migrationTable != null) {
                params.push("--migrationTable=" + settings.migrationTable);
            }
            params.push("--migrationPath=" + path);
            params.push("--interactive=0");
            params.push("--color");

            Process process = YiiCommandLineUtil.executeCommand(myProject, "migrate/up", params);
            if (process.waitFor() != 0) {
                return null;
            }
            String error = readStream(process.getErrorStream());
            if (error != null) {
                return null;
            }
            String stream = readStream(process.getInputStream());
            if (stream == null) {
                return null;
            }

            Set<String> result = new HashSet<>();
            if (stream.contains("No new migrations found. Your system is up-to-date.")) {
                return result;
            }

            Matcher matcher = migrateUpPattern.matcher(stream);
            while (matcher.find()) {
                result.add(matcher.group(1));
            }

            return result;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String readStream(InputStream stream) {
        try {
            int available = stream.available();
            if (available == 0) {
                return null;
            }
            byte[] data = new byte[available];
            int readBytes = stream.read(data);
            if (readBytes < available) {
                return null;
            }
            return new String(data);
        } catch (IOException e) {
            return null;
        }
    }
}
