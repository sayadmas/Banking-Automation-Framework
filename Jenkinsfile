pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                url: 'https://github.com/sayadmas/Banking-Automation-Framework.git'
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }
            stage('Run Regression Tests') {
            steps {
                sh 'mvn test -Dcucumber.filter.tags="@Regression"'
            }
        }
    }
    post {
        always {
            echo 'Automation execution completed'
        }
        success {
            echo 'Regression Passed'
        }
        failure {
            echo 'Regression Failed'
        }
    }
}
