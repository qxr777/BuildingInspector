# 使用openjdk的Java 17基础镜像
FROM openjdk:17-jdk

# 设置工作目录
WORKDIR /app

# 将构建的jar文件复制到容器中
COPY ruoyi-admin/target/ruoyi-admin.jar app.jar

# 暴露端口
EXPOSE 80

# 运行应用
ENTRYPOINT ["java", "-jar", "app.jar"]