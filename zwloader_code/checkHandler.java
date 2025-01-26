import et.telebof.BotClient;
import et.telebof.types.Message;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;
import java.io.File;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class CheckHandler {

    static Dotenv dotenv = Dotenv.configure()
            .directory("/app")
            .filename(".env")
            .load();
    static final String Screenshots = dotenv.get("SCREENSHOTS");// adjust accordingly
    private static final ExecutorService seleniumExecutor = Executors.newFixedThreadPool(4);

    public static void handleCheck(BotClient bot, Message message, long userId,
                                   String callbackData, // Новый параметр
                                   List<String> buttonLabels,
                                   Map<Long, List<Long>> userRequestTimestamps) {
        // Проверка лимита запросов
        if (isRateLimitExceeded(userId, userRequestTimestamps)) {
            bot.context.sendMessage("⚠️ Превышен лимит запросов. Пожалуйста, подождите 10 минут перед следующим запросом.").exec();
            return;
        }

        // Запись запроса пользователя
        recordUserRequest(userId, userRequestTimestamps);

        String targetGroup;
        if (message.text != null && message.text.startsWith("/check ")) {
            // Если вызов через команду
            String[] parts = message.text.split(" ", 2);
            if (parts.length < 2) {
                bot.context.sendMessage("Введите название группы после команды /check").exec();
                return;
            }
            targetGroup = parts[1].trim().toLowerCase();
        } else if (callbackData != null && callbackData.startsWith("/check ")) {
            // Если вызов через callback
            targetGroup = callbackData.split(" ")[1].trim().toLowerCase();
        } else {
            bot.context.sendMessage("Некорректный формат запроса").exec();
            return;
        }

        var messeage_edit = bot.context.sendMessage("Обрабатываю запрос для группы: " + targetGroup).exec();
        var messageID = messeage_edit.message_id;

        // Запуск задачи в потоке
        seleniumExecutor.submit(() -> {
            try {
                String result = selenium.performTask(targetGroup);
                bot.context.editMessageText("Результат: " + result, message.chat.id, messageID).exec();

                File file = new File(Screenshots + targetGroup.toUpperCase() + ".png");
                File file1 = new File(Screenshots + targetGroup.toUpperCase() + "x2.png");
                bot.context.sendDocument(message.chat.id, file).exec();
                bot.context.sendDocument(message.chat.id, file1).exec();
            } catch (Exception e) {
                bot.context.sendMessage("Ошибка при выполнении задачи, сори bipbop ").exec();
                System.out.println(e);
            }
        });
    }

    // преподы

    public static void handleCheckTeach(BotClient bot, Message message, long userId,
                                        String callbackData, // Новый параметр
                                        List<String> buttonLabelsTech,
                                        Map<Long, List<Long>> userRequestTimestamps) {
        // Проверка лимита запросов
        try {
            // БЛОК 1: Проверка лимита запросов
            if (isRateLimitExceeded(userId, userRequestTimestamps)) {
                bot.context.sendMessage("⚠️ Превышен лимит запросов. Пожалуйста, подождите 10 минут перед следующим запросом.").exec();
                return;
            }

            // БЛОК 2: Регистрация запроса в системе учета
            recordUserRequest(userId, userRequestTimestamps);

            // БЛОК 3: Определение имени преподавателя из входящего запроса
            String targetTeacher;
            if (message.text != null && message.text.startsWith("/checkt ")) {
                // Обработка прямой команды из чата
                String[] parts = message.text.split(" ", 2);
                System.err.println(parts);
                if (parts.length < 2) {
                    bot.context.sendMessage("Введите фамилию преподавателя после команды /checkt").exec();
                    return;
                }
                targetTeacher = parts[1].trim().toLowerCase();
                System.err.println(targetTeacher);
                bot.context.sendMessage(targetTeacher);
            } else if (callbackData != null && callbackData.startsWith("/checkt ")) {
                targetTeacher = callbackData.substring(8);
            } else {
                bot.context.sendMessage("Некорректный формат запроса").exec();
                return;
            }

            // БЛОК 4: Отправка сообщения о начале обработки
            var processingMessage = bot.context.sendMessage("Обрабатываю запрос для преподавателя: " + targetTeacher).exec();
            var messageID = processingMessage.message_id;

            // БЛОК 5: Асинхронное выполнение запроса
            seleniumExecutor.submit(() -> {
                try {
                    // Подблок 5.1: Получение данных через Selenium
                    String result = selenium.performTaskTech(targetTeacher);

                    // Подблок 5.2: Обновление статусного сообщения
                    bot.context.editMessageText("Результат: " + result, message.chat.id, messageID).exec();

                    // Подблок 5.3: Отправка скриншотов расписания
                    // Первый скриншот - основное расписание
                    File mainSchedule = new File(Screenshots + targetTeacher.toUpperCase() + ".png");
                    // Второй скриншот - дополнительная информация
                    File additionalSchedule = new File(Screenshots + targetTeacher.toUpperCase() + "x2.png");

                    // Отправка файлов пользователю
                    bot.context.sendDocument(message.chat.id, mainSchedule).exec();
                    bot.context.sendDocument(message.chat.id, additionalSchedule).exec();

                } catch (Exception e) {
                    // Подблок 5.4: Обработка ошибок
                    bot.context.sendMessage("Ошибка при выполнении задачи, сори bipbop ").exec();
                    System.out.println(e); // Логирование ошибки
                }
            });

        } catch (Exception e) {
            // БЛОК 6: Обработка общих ошибок
            bot.context.sendMessage("Произошла непредвиденная ошибка").exec();
            e.printStackTrace();
        }
    }

    private static boolean isRateLimitExceeded(long userId, Map<Long, List<Long>> userRequestTimestamps) {
        List<Long> timestamps = userRequestTimestamps.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        long currentTime = System.currentTimeMillis();
        timestamps.removeIf(timestamp -> currentTime - timestamp > 10 * 60 * 1000); // Remove old timestamps
        return timestamps.size() >= 5; // Maximum 5 requests per user
    }

    private static void recordUserRequest(long userId, Map<Long, List<Long>> userRequestTimestamps) {
        userRequestTimestamps.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(System.currentTimeMillis());
    }
}
