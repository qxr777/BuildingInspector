# 设置代理
export HTTPS_PROXY=http://127.0.0.1:7890

# IDEA maven窗口clean指令
/Users/qixin/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home/bin/java \
-Dmaven.multiModuleProjectDirectory=/Users/qixin/IdeaProjects/BuildingInspector/dev-BuildingInspector \
-Djansi.passthrough=true \
-Dmaven.home="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3" \
-Dclassworlds.conf="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/m2.conf" \
-Dmaven.ext.class.path="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven-event-listener.jar" \
-javaagent:"/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=50706" \
-Dfile.encoding=UTF-8 \
-classpath "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/boot/plexus-classworlds.license:/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/boot/plexus-classworlds-2.8.0.jar" \
org.codehaus.classworlds.Launcher \
-Didea.version=2024.3.5 clean

# IDEA maven窗口package指令
/Users/qixin/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home/bin/java \
-Dmaven.multiModuleProjectDirectory=/Users/qixin/IdeaProjects/BuildingInspector/dev-BuildingInspector \
-Djansi.passthrough=true \
-Dmaven.home="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3" \
-Dclassworlds.conf="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/m2.conf" \
-Dmaven.ext.class.path="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven-event-listener.jar" \
-javaagent:"/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=49830" \
-Dfile.encoding=UTF-8 \
-classpath "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/boot/plexus-classworlds.license:/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/boot/plexus-classworlds-2.8.0.jar" \
org.codehaus.classworlds.Launcher \
-Didea.version=2024.3.5 package

# 构建并推送多架构镜像
docker buildx build --platform linux/amd64,linux/arm64 -t crpi-gxv0234hwyx1jbhm.cn-beijing.personal.cr.aliyuncs.com/qxr777/dev-building-inspector:1.0 --push .