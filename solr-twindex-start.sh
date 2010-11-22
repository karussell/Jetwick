cd search
echo start solr at http://localhost:8082/solr
java -Djetty.port=8082 -Dsolr.solr.home=/home/peterk/Dokumente/quell/jetwick/twindex -jar start.jar
#java -Djetty.port=8082 -Dsolr.solr.home=/home/peterk/Dokumente/quell/jetwick/twindex -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000 -jar start.jar
#java -Djetty.port=8983 -Dsolr.solr.home=/home/peterk/Dokumente/quell/jetwick/twindex -jar start.jar
