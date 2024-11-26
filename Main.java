import et.telebof.BotClient;
import et.telebof.enums.MenuButtonType;
import et.telebof.types.*;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    // Карта для отслеживания запросов пользователей
    private static final Map<Long, List<Long>> userRequestTimestamps = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 5; // Максимальное количество запросов
    private static final long TIME_WINDOW = 10 * 60 * 1000; // 10 минут в миллисекундах

    private static boolean isRateLimitExceeded(long userId) {
        // Очистка старых запросов и проверка лимита
        List<Long> timestamps = userRequestTimestamps.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        long currentTime = System.currentTimeMillis();

        // Удаляем timestamps старше 10 минут
        timestamps.removeIf(timestamp -> currentTime - timestamp > TIME_WINDOW);

        // Проверяем количество запросов
        return timestamps.size() >= MAX_REQUESTS;
    }
    // Запись запроса пользователя
    private static void recordUserRequest(long userId) {
        // Добавляем текущий timestamp для пользователя
        userRequestTimestamps.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(System.currentTimeMillis());
    }
    // Загрузка списка кнопок из файла
    private static void loadButtonLabels(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                buttonLabels.add(line.trim());
            }
            System.out.println("Кнопки успешно загружены: " + buttonLabels);
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке кнопок: " + e.getMessage());
        }
    }
    // Загрузка переменных окружения
    static Dotenv dotenv = Dotenv.configure()
            .directory("./")
            .filename(".env")
            .load();

    static final String TOKEN = dotenv.get("TOKEN");
    static final String Groups = dotenv.get("Groups");
    static final String Screenshots = dotenv.get("Screenshots");
    // открытие потоков для таймера и selenium
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final ExecutorService seleniumExecutor = Executors.newFixedThreadPool(4);
    // Список для хранения названий групп
    private static List<String> buttonLabels = new ArrayList<>();

    public static void main(String[] args) {
        // Создание экземпляра бота
        final BotClient bot = new BotClient.Builder(TOKEN)
                .skipOldUpdates(true)
                .log(true)
                .build();
        // загружаем кнопки
        loadButtonLabels(Groups);

        bot.onMessage(filter -> filter.commands("start"), (context, message) -> {
            // Отправка стикера и сообщения как обычно
            bot.context.sendAnimation("CAACAgIAAxkBAAELTE5lu6ccE1JFDxbsKOmJouqFaLrpDwACOyIAAr7maUv7VPeYre_DojQE").exec();
            var messageto = bot.context.sendMessage("Приветствую. Это бот с расписанием для эти сгту. Основные команды вы увидите в кнопках желаю удачи. Cвязь с админом сия творения @inliktor.Сообщение поменяется через 5 секунд").exec();
            int messageID = messageto.message_id;
            MenuButton menuButton = new MenuButton(MenuButtonType.WEB_APP)  // или другой тип из MenuButtonType
                    .text("не думай!")  // устанавливаем текст кнопки
                    .webApp(new WebAppInfo("https://ipinfo.io/"));  // URL вашего веб-приложения

            // Устанавливаем кнопку меню для чата
            context.setChatMenuButton()
                    .chatId(message.chat.id)  // добавляем chat_id
                    .menuButton(menuButton)   // устанавливаем кнопку
                    .exec();
            //  таймер для изменения сообщения
            scheduler.schedule(() -> {
                ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup().resizeKeyboard(true);
                for (String label : buttonLabels) {
                    markup.add("/check "+label);  // Добавляем каждую строку как кнопку
                }
                context.editMessageText("Выбери какую группу мне тебе прислать:",message.chat.id,messageID).exec();
                context.sendMessage("")
                        .replyMarkup(markup)
                        .exec();
            }, 5, TimeUnit.SECONDS);
        });

        bot.onMessage(filter -> filter.commands("check"), (context, message) -> {
            long userId = message.from.id;


            // Проверка лимита запросов
            if (isRateLimitExceeded(userId)) {
                // Отправка сообщения о превышении лимита
                context.sendMessage("⚠️ Превышен лимит запросов. Пожалуйста, подождите 10 минут перед следующим запросом.").exec();
                return;
            }

            // Записываем запрос пользователя
            recordUserRequest(userId);
            System.out.println(userRequestTimestamps);
            String[] parts = message.text.split(" ", 2);
            if (parts.length < 2) {
                context.sendMessage("Введите название группы после команды /check").exec();
                return;
            }

            String targetGroup = parts[1].trim().toLowerCase();
            var messeage_edit = context.sendMessage("Обрабатываю запрос для группы: " + targetGroup).exec();
            var messageID = messeage_edit.message_id;
            // он в потоке seleniumExecutor
            seleniumExecutor.submit(() -> {
                try {
                    String result = selenium.performTask(targetGroup);
                    System.out.println("Результат Selenium: " + result);
                    context.editMessageText("Результат: " + result, message.chat.id, messageID).exec();

                    File file = new File(Screenshots + targetGroup.toUpperCase() + ".png");
                    File file1 = new File(Screenshots+ targetGroup.toUpperCase() + "x2" + ".png");
                    context.sendDocument(message.chat.id, file).exec();
                    context.sendDocument(message.chat.id, file1).exec();
                } catch (Exception e) {
                    context.sendMessage("Ошибка при выполнении задачи, сори bipbop ").exec();
                }
            });
        });

        bot.run();
    }
}
