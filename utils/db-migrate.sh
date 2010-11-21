cd ~/Dokumente/quell/jetwick/
source ./utils/init.sh

#CMD=changelogSyncSQL
#CMD=changelogSync
CMD=update
#CMD=updateSQL
#CMD="rollbackCount 1"

dbenv=production

if [ "$1" == "production" ]; then
 dbenv=$1 
fi

liquibase --logLevel=FINE \
          --driver=org.h2.Driver \
          --changeLogFile=src/main/resources/dbchangelog.xml \
          --url=jdbc:h2:~/.jetwick/$dbenv \
          --username=$USER --password=$PW \
        $CMD

#liquibase --logLevel=FINE \
#          --driver=org.hsqldb.jdbcDriver \
#          --changeLogFile=src/main/resources/dbchangelog.xml \
#          --url=jdbc:hsqldb:/home/peterk/.jetwick/$dbenv \
#          --username=$USER --password=$PW \
#        $CMD