cd search
echo start advertisment index at http://localhost:8083/solr
java -Djetty.port=8083 -Dsolr.solr.home=/home/peterk/Dokumente/quell/jetwick/adindex -jar start.jar