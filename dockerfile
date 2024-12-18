
FROM eclipse-temurin:23-jdk

# делаем папку название если меняете то меняйте и в main так как там прописан env
WORKDIR /app


RUN apt-get update && apt-get install -y maven


COPY pom.xml /app/

# в вашей системе, есть папка .m2 и там вы должны сделать вот такое
# <settings>
#   <servers>
#     <server>
#       <id>github</id>
#       <username>ВАШ_GITHUB_USERNAME</username>
#       <password>ВАШ_GITHUB_TOKEN</password>
#     </server>
#   </servers>
# </settings>
# права для settings.xml
# chmod 600 ~/.m2/settings.xml  если вы на линукс
COPY settings.xml /root/.m2/settings.xml

# туда пойдут скрины и группа
RUN mkdir screenshots_sel
# тяжек путь, для того, чтобы заиметь этот файл в селениуме есть функция, выполните ее, либа дождитесь пока я сделаю автоматизацию
COPY Groups.txt /app/screenshots_sel/

COPY java /app/src/main/java
# важно
COPY .env /app/.env



RUN mvn clean install -U


CMD ["mvn", "exec:java", "-Dexec.mainClass=Main"]
