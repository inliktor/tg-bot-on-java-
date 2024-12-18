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
            .directory("./")
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
