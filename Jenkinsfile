pipeline {
    agent any
    environment {
        PATH = "/opt/homebrew/bin:$PATH"
    }
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
            echo "Test execution completed"
        }
    }
}
