pipeline {

    agent any

    environment {
        PATH = "/opt/homebrew/bin:$PATH"
    }

    stages {

        stage('Check Environment') {

            steps {

                sh '''
                echo "PATH:"
                echo $PATH

                echo "Java:"
                java -version

                echo "Maven:"
                mvn -version
                '''

            }
        }


        stage('Build') {

            steps {

                sh 'mvn clean compile'

            }

        }


        stage('Regression Test') {

            steps {

                sh 'mvn test -Dcucumber.filter.tags="@Regression"'

            }

        }

    }


    post {

        always {

            echo "Execution completed"

        }

    }

}
