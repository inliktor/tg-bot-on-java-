
import et.telebof.BotClient;
import et.telebof.enums.MenuButtonType;
import et.telebof.enums.PollType;
import et.telebof.filters.CustomFilter;
import et.telebof.filters.Filter;
import io.github.cdimascio.dotenv.Dotenv;
import et.telebof.types.*;
import et.telebof.types.InlineKeyboardMarkup;
import et.telebof.types.InlineKeyboardButton;

import et.telebof.types.InlineQueryResult;
import et.telebof.types.InlineQueryResultArticle;
import et.telebof.types.InputTextMessageContent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.io.File;
import java.util.Map;

class UnknownCommandFilter implements CustomFilter {
    private final BotCommand[] knownCommands;

    public UnknownCommandFilter(BotCommand[] commands) {
        this.knownCommands = commands;
    }

    @Override
    public boolean check(Update update) {
        if (update.message.text == null || !update.message.text.startsWith("/")) {
            return false;
        }

        String command = update.message.text.split(" ")[0];

        for (BotCommand knownCommand : knownCommands) {
            if (command.equals(knownCommand.command)) {
                return false;
            }
        }

        return true;
    }
}

public class Main {
    private static final Map<Long, List<Long>> userRequestTimestamps = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 5;
    private static final long TIME_WINDOW = 10 * 60 * 1000;

    private static boolean isRateLimitExceeded(long userId) {
        List<Long> timestamps = userRequestTimestamps.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        long currentTime = System.currentTimeMillis();

        timestamps.removeIf(timestamp -> currentTime - timestamp > TIME_WINDOW);

        return timestamps.size() >= MAX_REQUESTS;
    }

    private static void recordUserRequest(long userId) {
        userRequestTimestamps.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(System.currentTimeMillis());
    }

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

    private static List<String> filterGroupsByCourse(String group, String course) {
        List<String> filteredGroups = new ArrayList<>();

        for (String label : buttonLabels) {
            if (label.startsWith(group + "-" + course)) {
                filteredGroups.add(label);
            }
        }

        return filteredGroups;
    }

    private static void loadButtonTeacher(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                buttonLabelsTeacher.add(line.trim());
            }
            System.out.println("Кнопки успешно загружены: " + buttonLabelsTeacher);
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке кнопок: " + e.getMessage());
        }
    }
    private static ReplyKeyboardMarkup createCheckCommandsMarkup(List<String> groups) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup().resizeKeyboard(true);

        for (String group : groups) {
            markup.add("/check " + group);
        }

        return markup;
    }

    static Dotenv dotenv = Dotenv.configure()
            .directory("/app")
            .filename(".env")
            .load();


    static final String TOKEN = dotenv.get("TOKEN");
    static final String Groups = dotenv.get("GROUPS");
    static final String Screenshots = dotenv.get("SCREENSHOTS");
    static final String TEACHERS  = dotenv.get("TEACHERS");

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final ExecutorService seleniumExecutor = Executors.newFixedThreadPool(4);

    private static List<String> buttonLabels = new ArrayList<>();
    private static List<String> buttonLabelsTeacher = new ArrayList<>();

    public static void main(String[] args) {
        // Создание экземпляра бота
        final BotClient bot = new BotClient.Builder(TOKEN)
                .skipOldUpdates(true)
                .log(true)
                .build();

        // Загрузка кнопок
        loadButtonLabels(Groups);
        loadButtonTeacher(TEACHERS);

        // Обработчик команды /start - новая реализация с кнопками специальностей
        bot.onMessage(filter -> filter.commands("start"), (context, message) -> {
            InlineKeyboardMarkup specialtyMarkup = new InlineKeyboardMarkup();
            specialtyMarkup.addKeyboard(
                    new InlineKeyboardButton("ИСП").callbackData("group_исп"),
                    new InlineKeyboardButton("МТО").callbackData("group_мто"),
                    new InlineKeyboardButton("ОДЛ").callbackData("group_одл"),
                    new InlineKeyboardButton("ТМС").callbackData("group_тмс"),
                    new InlineKeyboardButton("ТОД").callbackData("group_тод"),
                    new InlineKeyboardButton("УКП").callbackData("group_укп"),
                    new InlineKeyboardButton("ЭКБУ").callbackData("group_экбу"),
                    new InlineKeyboardButton("ОСА").callbackData("group_оса"),
                    new InlineKeyboardButton("Преподаватели").callbackData("teacher")
            );

            var messageto = bot.context.sendMessage("Привет! Выберите вашу специальность:")
                    .replyMarkup(specialtyMarkup)
                    .exec();
        });

        // Обработчики callback для выбора специальности, курса и группы
        // Обработчик callback-событий для телеграм-бота по выбору группы и курса
        bot.onCallback((context, callback) -> {
            // Извлечение данных callback и идентификаторов чата и пользователя
            String callbackData = callback.data;  // Данные, полученные при нажатии кнопки
            long chatId = callback.message.chat.id;  // Идентификатор чата
            long userId = callback.from.id;  // Идентификатор пользователя

            // БЛОК 1: Обработка выбора специальности
            if (callbackData.startsWith("group_")) {
                // Извлечение названия специальности из callback-данных
                // Например, "group_исп" -> "исп"
                String group = callbackData.split("_")[1];

                // Создание клавиатуры для выбора курса
                InlineKeyboardMarkup courseMarkup = new InlineKeyboardMarkup();
                courseMarkup.addKeyboard(
                        // Кнопки курсов с динамическим callback-data
                        new InlineKeyboardButton("1 курс").callbackData("course_" + group + "_1"),
                        new InlineKeyboardButton("2 курс").callbackData("course_" + group + "_2"),
                        new InlineKeyboardButton("3 курс").callbackData("course_" + group + "_3"),

                        // Кнопка возврата к выбору специальности
                        new InlineKeyboardButton("← Назад").callbackData("back_specialty")
                );

                // Изменение текущего сообщения для отображения выбора курса
                context.editMessageText("Выберите курс:", chatId, callback.message.message_id)
                        .replyMarkup(courseMarkup)
                        .exec();

            }
            // БЛОК 2: Обработка выбора курса
            else if (callbackData.startsWith("course_")) {
                // Разделение callback-данных на части
                // Например, "course_исп_2" -> ["course", "исп", "2"]
                String[] parts = callbackData.split("_");
                String group = parts[1];      // Специальность
                String course = parts[2];      // Курс

                // Фильтрация групп для выбранной специальности и курса
                List<String> filteredGroups = filterGroupsByCourse(group.toUpperCase(), course);

                // Проверка наличия групп
                if (filteredGroups.isEmpty()) {
                    context.answer("Группы для этого курса не найдены.").exec();
                    return;
                }

                // Создание клавиатуры для выбора конкретной группы
                InlineKeyboardMarkup groupMarkup = new InlineKeyboardMarkup();

                // Динамическое создание кнопок для найденных групп
                for (String groupLabel : filteredGroups) {
                    groupMarkup.addKeyboard(
                            // Каждая кнопка ведет к проверке группы
                            new InlineKeyboardButton(groupLabel).callbackData("/check " + groupLabel)
                    );
                }

                // Кнопка возврата к выбору курса
                groupMarkup.addKeyboard(
                        new InlineKeyboardButton("← Назад").callbackData("back_course_" + group)
                );

                // Изменение текущего сообщения для отображения списка групп
                context.editMessageText("Выберите вашу группу:", chatId, callback.message.message_id)
                        .replyMarkup(groupMarkup)
                        .exec();

            }
            // БЛОК 3: Возврат к выбору специальности
            else if (callbackData.equals("back_specialty")) {
                // Создание клавиатуры со списком специальностей
                InlineKeyboardMarkup specialtyMarkup = new InlineKeyboardMarkup();
                specialtyMarkup.addKeyboard(
                        new InlineKeyboardButton("ИСП").callbackData("group_исп"),
                        new InlineKeyboardButton("МТО").callbackData("group_мто"),
                        new InlineKeyboardButton("ОДЛ").callbackData("group_одл"),
                        new InlineKeyboardButton("ТМС").callbackData("group_тмс"),
                        new InlineKeyboardButton("ТОД").callbackData("group_тод"),
                        new InlineKeyboardButton("УКП").callbackData("group_укп"),
                        new InlineKeyboardButton("ЭКБУ").callbackData("group_экбу"),
                        new InlineKeyboardButton("ОСА").callbackData("group_оса"),
                        new InlineKeyboardButton("Преподаватели").callbackData("teacher")
                );

                // Возврат к экрану выбора специальности
                context.editMessageText("Выберите вашу специальность:", chatId, callback.message.message_id)
                        .replyMarkup(specialtyMarkup)
                        .exec();

            }
            // БЛОК 4: Возврат к выбору курса
            else if (callbackData.startsWith("back_course_")) {
                // Извлечение специальности для возврата
                String group = callbackData.split("_")[2];

                // Воссоздание клавиатуры курсов
                InlineKeyboardMarkup courseMarkup = new InlineKeyboardMarkup();
                courseMarkup.addKeyboard(
                        new InlineKeyboardButton("1 курс").callbackData("course_" + group + "_1"),
                        new InlineKeyboardButton("2 курс").callbackData("course_" + group + "_2"),
                        new InlineKeyboardButton("3 курс").callbackData("course_" + group + "_3"),
                        new InlineKeyboardButton("← Назад").callbackData("back_specialty")
                );

                // Возврат к экрану выбора курса
                context.editMessageText("Выберите курс:", chatId, callback.message.message_id)
                        .replyMarkup(courseMarkup)
                        .exec();

            }
            else if (callbackData.startsWith("teacher")) {
                InlineKeyboardMarkup groupMarkup = new InlineKeyboardMarkup();

                // Динамическое создание кнопок для найденных групп
                for (String groupLabelTeach : buttonLabelsTeacher) {
                    groupMarkup.addKeyboard(
                            // Каждая кнопка ведет к проверке препода
                            new InlineKeyboardButton(groupLabelTeach).callbackData("/checkt " + groupLabelTeach)
                    );
                }

                // Кнопка возврата к выбору курса
                groupMarkup.addKeyboard(
                        new InlineKeyboardButton("← Назад").callbackData("back_specialty")
                );

                // Изменение текущего сообщения для отображения списка препода
                context.editMessageText("Выберите вашего препода:", chatId, callback.message.message_id)
                        .replyMarkup(groupMarkup)
                        .exec();
            }
            // БЛОК 5: Финальный выбор группы
            else if (callbackData.startsWith("/check ")) {
                // Вызов специального обработчика для проверки выбранной группы
                // Передача всех необходимых параметров для дальнейшей обработки
                CheckHandler.handleCheck(
                        bot,
                        callback.message,
                        userId,
                        callbackData,
                        buttonLabels,
                        userRequestTimestamps
                );
            }
            else if (callbackData.startsWith("/checkt ")) {
                // Вызов специального обработчика для проверки выбранной группы
                // Передача всех необходимых параметров для дальнейшей обработки
                CheckHandler.handleCheckTeach(
                        bot,
                        callback.message,
                        userId,
                        callbackData,
                        buttonLabelsTeacher,
                        userRequestTimestamps
                );
            }
        });

        // Обработчик команды /check
        bot.onMessage(filter -> filter.commands("check"), (context, message) -> {
            long userId = message.from.id;

            if (isRateLimitExceeded(userId)) {
                bot.context.sendMessage("⚠️ Превышен лимит запросов. Пожалуйста, подождите 10 минут перед следующим запросом.").exec();
                return;
            }

            recordUserRequest(userId);
            System.out.println(userRequestTimestamps);

            String[] parts = message.text.split(" ", 2);
            if (parts.length < 2) {
                bot.context.sendMessage("Введите название группы после команды /check").exec();
                return;
            }

            String targetGroup = parts[1].trim().toLowerCase();
            var messeage_edit = bot.context.sendMessage("Обрабатываю запрос для группы: " + targetGroup).exec();
            var messageID = messeage_edit.message_id;

            seleniumExecutor.submit(() -> {
                try {
                    String result = selenium.performTask(targetGroup);
                    System.out.println("Результат Selenium: " + result);
                    bot.context.editMessageText("Результат: " + result, message.chat.id, messageID).exec();

                    File file = new File(Screenshots + targetGroup.toUpperCase() + ".png");
                    File file1 = new File(Screenshots+ targetGroup.toUpperCase() + "x2" + ".png");
                    bot.context.sendDocument(message.chat.id, file).exec();
                    bot.context.sendDocument(message.chat.id, file1).exec();
                } catch (Exception e) {
                    bot.context.sendMessage("Ошибка при выполнении задачи, сори bipbop ").exec();
                    System.out.println(e);
                }
            });
        });

        // Список команд бота
        BotCommand[] BotCommand = {
                new BotCommand("/start","Начать работу с ботом"),
                new BotCommand("/check","Получить расписание группы"),
                new BotCommand("/quarantine","Опрос для карантина"),
                new BotCommand("/kill","Убирает кнопки у пользователя"),
                new BotCommand("/help","Получить список команд")
        };
        bot.context.setMyCommands(BotCommand).exec();

        // Обработчики других команд остались прежними
        bot.onMessage(filter -> filter.commands("help"), (context, message) -> {
            bot.context.sendMessage("Доступные команды:\n" +
                    "/start - Начать работу с ботом\n" +
                    "/check - Получить расписание группы\n" +
                    "/quarantine - Опрос для карантина\n" +
                    "/kill - Убирает кнопки\n" +
                    "/help - Получить список команд").exec();
        });

        bot.onMessage(filter -> filter.commands("quarantine"), (context, message) -> {

            bot.context.sendMessage("После выбора ответа, вы его поменять не сможете, выбирайте с умом").exec();
            String question = "Болеете ли вы со справкой?";
            InputPollOption[] options = {
                    new InputPollOption("Да"),
                    new InputPollOption("Нет"),
                    new InputPollOption("Не болею"),
                    new InputPollOption("Пойду за справкой"),
                    new InputPollOption("Для куратора,чтобы посмотреть кто как проголосовал")
            };
            bot.context.sendPoll(message.chat.id, question, options)
                    .isAnonymous(false)
                    .type(PollType.QUIZ)
                    .correctOptionId(2)
                    .explanation("Мы институт, мы не знаем что такое карантин")
                    .exec();
        });
        bot.onMessage(filter -> filter.commands("kill"), (context, message) -> {
            ReplyKeyboardRemove removeKeyboard = new ReplyKeyboardRemove().selective(true);
            bot.context.sendMessage("Кнопки капут")
                    .replyMarkup(removeKeyboard)
                    .exec();
        });
        bot.onMessage(filter -> filter.commands("Groups"), (context, message) -> {
            Long user = message.chat.id;
            if (user == "your id"){
                try {
                    selenium.writeGroups();
                    selenium.writeTeacher();
                    bot.context.sendMessage("Готово!");
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            else context.sendMessage("Вы ошиблись!");
        });
        bot.onMessage(
                filter -> filter.text() && filter.customFilter(new UnknownCommandFilter(BotCommand)),
                (context, message) -> {
                    context.sendMessage("Извините, такой команды не существует. Пожалуйста, введите /start для получения списка доступных команд.").exec();
                }
        );

        bot.run();
    }
}