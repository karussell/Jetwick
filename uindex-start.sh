cd search
echo user index at http://localhost:8081/solr
java -Djetty.port=8081 -Dsolr.solr.home=/home/peterk/Dokumente/quell/jetwick/uindex -jar start.jar
