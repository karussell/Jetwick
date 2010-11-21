DETAILS=false

# returns the free PermGen space in MB
SPACE=`./myjava de.jetwick.util.PermGenDetect "service:jmx:rmi:///jndi/rmi://81.169.187.238:9347/jmxrmi" $DETAILS`
if [ "$SPACE" -lt 50 ]; then
  read -p "CRITICAL: only $SPACE MB PermGen space free! Press Ctrl-C to abort and restart tomcat!"
else
  echo free PermGen space: $SPACE MB
fi
