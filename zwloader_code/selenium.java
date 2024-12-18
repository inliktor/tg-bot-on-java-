import com.google.common.io.Files;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


import io.github.cdimascio.dotenv.Dotenv;

public class selenium {
    static Dotenv dotenv = Dotenv.configure()
            .directory("./")
            .filename(".env") // instead of '.env', use 'env'
            .load();
    static final String HUB = dotenv.get("HUB");
    static final String Groups = dotenv.get("GROUPS");
    static final String Site = dotenv.get("SITE");
    static final String Screenshots = dotenv.get("SCREENSHOTS");

//        public static void main(String[] args) throws MalformedURLException {
//            writeGroups();
//    }
    public static String performTask(String targetGroupName) {
        WebDriver driver = null;
        String result = "";

        try {
            // Настройка подключения к Selenium Grid
            URL hubUrl = new URL(HUB);

            FirefoxOptions options = new FirefoxOptions();

            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            options.addArguments("--headless");  // Запуск браузера в фоновом режиме
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-gpu");
            options.addArguments("--ignore-certificate-errors");
            options.addArguments("--ignore-ssl-errors");

            // Создание WebDriver
            driver = new RemoteWebDriver(hubUrl, options);

            // Переход на страницу
            driver.get(Site);

            // Поиск всех ссылок с классом 'z0'
            List<WebElement> elements = driver.findElements(By.xpath("//a[contains(@class, 'z0')]"));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(Groups))) {
                for (WebElement element : elements) {
                    writer.write(element.getText());
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
//            List<WebElement> elements = driver.findElements(By.cssSelector("a.z0"));
            // Поиск нужной группы
            boolean groupFound = false;
            for (WebElement element : elements) {
                String groupName = element.getText().toLowerCase(); // Текст ссылки в нижнем регистре
                if (groupName.equals(targetGroupName.toLowerCase())) { // Сравниваем с названием группы
                    String href = element.getAttribute("href"); // Получаем значение href
                    result = "Группа: " + groupName.toUpperCase() + ", Ссылка: " + href; // Приводим группу к верхнему регистру
                    groupFound = true;
//                    System.out.printf(result.split(", Ссылка:")[1]);
                    break; // Выходим из цикла после нахождения группы
                }
            }

            // Если группа найдена/ бтв это все еще говнокод )
            if (groupFound) {
                String taggroup;
                String groupName = result.split(",")[0].replace("Группа: ", "");
//                System.out.println(groupName);
                taggroup = result.split(",")[0];
                taggroup = taggroup.replace("Группа: ", "");
                String href=result.split(", Ссылка:")[1];
//                System.out.println(href);
                driver.get(href);
                if (groupName.contains("МТО") || groupName.contains("ЭКБУ") || groupName.contains("ОСА") || groupName.contains("УКП")){
                    driver.manage().window().setSize(new Dimension(3000, 2000));// Для "мто" размер экрана больше
                    File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                    Files.copy(screenshot, new File(Screenshots+taggroup+".png"));
                    driver.manage().window().setSize(new Dimension(3000, 3000));
                    screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                    Files.copy(screenshot, new File(Screenshots+taggroup +"x2"+".png"));
                } else {
                    driver.manage().window().setSize(new Dimension(1250, 1800));  // Стандартный размер экрана
                    File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                    Files.copy(screenshot, new File(Screenshots+taggroup+".png"));
                    driver.manage().window().setSize(new Dimension(1250, 3000));
                    screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                    Files.copy(screenshot, new File(Screenshots+taggroup +"x2"+".png"));
                }
            }
            else{
                result = "Группа \"" + targetGroupName + "\" не найдена.";
            }

        } catch (MalformedURLException e) {
            result = "Ошибка: Неверный URL хаба!";
        } catch (Exception e) {
            result = "Ошибка при выполнении задачи: " + e.getMessage();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        return result;
    }
    public static void  writeGroups() throws MalformedURLException {
        WebDriver driver = null;
        String result = "";

        try {
            // Настройка подключения к Selenium Grid
            URL hubUrl = new URL(HUB);

            FirefoxOptions options = new FirefoxOptions();

            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            options.addArguments("--headless");  // Запуск браузера в фоновом режиме
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-gpu");
            options.addArguments("--ignore-certificate-errors");
            options.addArguments("--ignore-ssl-errors");

            // Создание WebDriver
            driver = new RemoteWebDriver(hubUrl, options);

            // Переход на страницу
            driver.get(Site);

            // Поиск всех ссылок с классом 'z0'
            List<WebElement> elements = driver.findElements(By.xpath("//a[contains(@class, 'z0')]"));
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(Groups))) {
                for (WebElement element : elements) {
                    writer.write(element.getText());
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            driver.quit();
        }
    }
}
