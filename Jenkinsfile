pipeline {

    agent any

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
    }
}
