$host.ui.RawUI.WindowTitle = "{{SERVER_NAME}}"

# No idea why --add-opens works. Got the "tip" from
# https://github.com/SmartDataAnalytics/jena-sparql-api/issues/43
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED  -Xmx{{SERVER_MEMORY}} -Xms{{SERVER_MEMORY}} -enableassertions -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar server.jar --nogui

Read-Host "Press ENTER to continue..."
