cd ~/Dokumente/quell/jetwick/     
source ./utils/init.sh

# put h2 jar into liquibase/lib/
# and include hibernate jar for liquibase itself!

dbenv=production
# create the reference database
rm ~/.jetwick/migrate.*
./myjava de.jetwick.hib.Migrate

echo 'update change log'
liquibase --driver=org.h2.Driver \
          --url=jdbc:h2:~/.jetwick/migrate \
          --username=$USER --password=$PW \
          --changeLogFile=src/main/resources/dbchangelog.xml \
      diffChangeLog \
          --baseUrl=jdbc:h2:~/.jetwick/$dbenv --baseUsername=$USER --basePassword=$PW
          
echo '##################################################################'
echo 'DO NOT forget to 1. run db-migrate.sh and 2. fill in some more keywords other than the default'

#liquibase --driver=org.hsqldb.jdbcDriver \
#          --url=jdbc:hsqldb:/home/peterk/.jetwick/migrate \
#          --username=$USER --password=$PW \
#          --changeLogFile=src/main/resources/dbchangelog.xml \
#      diffChangeLog \
#          --baseUrl=jdbc:hsqldb:/home/peterk/.jetwick/$dbenv --baseUsername=$USER --basePassword=$PW