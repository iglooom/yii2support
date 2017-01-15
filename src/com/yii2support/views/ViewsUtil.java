package com.yii2support.views;

import com.intellij.codeInsight.template.macro.SplitWordsMacro;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;

/**
 * Created by NVlad on 15.01.2017.
 */
public class ViewsUtil {
    static PsiFile getViewPsiFile(PsiElement psiElement) {
        PsiFile psiFile = psiElement.getContainingFile();
        StringLiteralExpression expression = (StringLiteralExpression) psiElement;
        String filename = expression.getContents();
        if (filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf("/") + 1);
        }
        if (!filename.contains(".")) {
            filename = filename.concat(".php");
        }

        PsiDirectory directory = getViewsPsiDirectory(psiFile, psiElement);

        if (directory == null) {
            return null;
        }

        return directory.findFile(filename);
    }

    static PsiDirectory getViewsPsiDirectory(PsiFile psiFile, PsiElement psiElement) {
        String fileName = psiFile.getName().substring(0, psiFile.getName().lastIndexOf("."));
        PsiDirectory psiDirectory = psiFile.getContainingDirectory();

        if (fileName.endsWith("Controller")) {
            psiDirectory = psiFile.getContainingDirectory().getParentDirectory();
            if (psiDirectory != null) {
                psiDirectory = psiDirectory.findSubdirectory("views");

                if (psiDirectory != null) {
                    String container = fileName.substring(0, fileName.length() - 10);
                    container = new SplitWordsMacro.LowercaseAndDash().convertString(container);

                    psiDirectory = psiDirectory.findSubdirectory(container);
                }
            }
        }

        String enteredText = ((StringLiteralExpression) psiElement).getContents();
        String enteredPath = enteredText;
        if (enteredText.startsWith("/")) {
            while (psiDirectory != null && !psiDirectory.getName().equals("views")) {
                psiDirectory = psiDirectory.getParentDirectory();
            }
            enteredPath = enteredPath.substring(1);
        }

        if (!enteredPath.endsWith("/") && enteredPath.contains("/")) {
            enteredPath = enteredPath.substring(0, enteredPath.lastIndexOf("/") + 1);
            if (enteredPath.length() == 1) {
                enteredPath = "";
            }
        }

        if (enteredPath.endsWith("/")) {
            String directory;
            while (!enteredPath.equals("")) {
                directory = enteredPath.substring(0, enteredPath.indexOf("/"));
                enteredPath = enteredPath.substring(directory.length() + 1);
                if (psiDirectory != null) {
                    psiDirectory = psiDirectory.findSubdirectory(directory);
                }
            }
        }

        return psiDirectory;
    }
}
