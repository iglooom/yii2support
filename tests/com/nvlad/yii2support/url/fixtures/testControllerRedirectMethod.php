<?php

namespace app\controllers {
    class TestController extends \yii\web\Controller
    {
        public function actionRedirectTest($name)
        {
            $this->redirect('<caret>');
        }
    }
}