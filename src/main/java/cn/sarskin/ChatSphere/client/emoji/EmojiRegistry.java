package cn.sarskin.ChatSphere.client.emoji;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EmojiRegistry {
    private static final List<EmojiEntry> ALL = new ArrayList<>();
    private static final Map<String, EmojiEntry> BY_SHORTCODE = new LinkedHashMap<>();
    private static final Map<String, List<EmojiEntry>> BY_CATEGORY = new LinkedHashMap<>();
    private static final List<String> CATEGORIES = new ArrayList<>();
    public static final Map<String, String> BY_CHAR = new HashMap<>();
    public static final Map<String, String> CATEGORY_LANG_KEYS = new LinkedHashMap<>();

    private static final Pattern SHORTCODE_PATTERN = Pattern.compile(":([a-zA-Z0-9_+-]+):");
    private static final Map<String, String> PUA_BY_SHORTCODE = new LinkedHashMap<>();
    private static final Map<Integer, String> UNICODE_FIRST_CP_TO_PUA = new HashMap<>();
    private static final Map<Integer, List<UnicodeEntry>> UNICODE_CANDIDATES = new HashMap<>();
    private static final Set<Integer> EMOJI_FIRST_CODEPOINTS = new HashSet<>();
    private static final int PUA_START = 0xE000;

    private record UnicodeEntry(String unicode, String pua) {}

    public static final Set<String> HIDDEN = Set.of(":rainbow_flag:", ":pirate_flag:");

    public static List<EmojiEntry> getVisible() {
        return ALL.stream().filter(e -> !HIDDEN.contains(e.shortcode())).toList();
    }

    public static List<EmojiEntry> getVisibleByCategory(String category) {
        return byCategory(category).stream().filter(e -> !HIDDEN.contains(e.shortcode())).toList();
    }

    static {
        CATEGORY_LANG_KEYS.put("Smileys & Emotion", "emoji.category.smileys");
        CATEGORY_LANG_KEYS.put("People & Body", "emoji.category.people");
        CATEGORY_LANG_KEYS.put("Animals & Nature", "emoji.category.animals");
        CATEGORY_LANG_KEYS.put("Food & Drink", "emoji.category.food");
        CATEGORY_LANG_KEYS.put("Activities", "emoji.category.activities");
        CATEGORY_LANG_KEYS.put("Travel & Places", "emoji.category.travel");
        CATEGORY_LANG_KEYS.put("Objects", "emoji.category.objects");
        CATEGORY_LANG_KEYS.put("Symbols", "emoji.category.symbols");
        CATEGORY_LANG_KEYS.put("Flags", "emoji.category.flags");

        register(":grin:", "\uD83D\uDE01", "Smileys & Emotion", "Grinning Face");
        register(":smiley:", "\uD83D\uDE03", "Smileys & Emotion", "Smiling Face with Open Mouth");
        register(":smile:", "\uD83D\uDE04", "Smileys & Emotion", "Smiling Face with Open Mouth and Smiling Eyes");
        register(":sweat_smile:", "\uD83D\uDE05", "Smileys & Emotion", "Grinning Face with Sweat");
        register(":laughing:", "\uD83D\uDE06", "Smileys & Emotion", "Smiling Face with Open Mouth and Tightly-Closed Eyes");
        register(":joy:", "\uD83D\uDE02", "Smileys & Emotion", "Face with Tears of Joy");
        register(":rofl:", "\uD83E\uDD23", "Smileys & Emotion", "Rolling on the Floor Laughing");
        register(":relaxed:", "\u263A\uFE0F", "Smileys & Emotion", "White Smiling Face");
        register(":blush:", "\uD83D\uDE0A", "Smileys & Emotion", "Smiling Face with Smiling Eyes");
        register(":innocent:", "\uD83D\uDE07", "Smileys & Emotion", "Smiling Face with Halo");
        register(":wink:", "\uD83D\uDE09", "Smileys & Emotion", "Winking Face");
        register(":heart_eyes:", "\uD83D\uDE0D", "Smileys & Emotion", "Smiling Face with Heart-Eyes");
        register(":kissing_heart:", "\uD83D\uDE18", "Smileys & Emotion", "Face Throwing a Kiss");
        register(":kissing:", "\uD83D\uDE17", "Smileys & Emotion", "Kissing Face");
        register(":yum:", "\uD83D\uDE0B", "Smileys & Emotion", "Face Savouring Delicious Food");
        register(":stuck_out_tongue:", "\uD83D\uDE1B", "Smileys & Emotion", "Face with Stuck-Out Tongue");
        register(":stuck_out_tongue_winking_eye:", "\uD83D\uDE1C", "Smileys & Emotion", "Face with Stuck-Out Tongue and Winking Eye");
        register(":zany:", "\uD83E\uDD2A", "Smileys & Emotion", "Zany Face");
        register(":facepalm:", "\uD83E\uDD26", "Smileys & Emotion", "Face Palm");
        register(":shrug:", "\uD83E\uDD37", "Smileys & Emotion", "Shrug");
        register(":nerd:", "\uD83E\uDD13", "Smileys & Emotion", "Nerd Face");
        register(":cool:", "\uD83D\uDE0E", "Smileys & Emotion", "Smiling Face with Sunglasses");
        register(":thinking:", "\uD83E\uDD14", "Smileys & Emotion", "Thinking Face");
        register(":neutral:", "\uD83D\uDE10", "Smileys & Emotion", "Neutral Face");
        register(":expressionless:", "\uD83D\uDE11", "Smileys & Emotion", "Expressionless Face");
        register(":no_mouth:", "\uD83D\uDE36", "Smileys & Emotion", "Face Without Mouth");
        register(":unamused:", "\uD83D\uDE12", "Smileys & Emotion", "Unamused Face");
        register(":roll_eyes:", "\uD83D\uDE44", "Smileys & Emotion", "Face with Rolling Eyes");
        register(":smirk:", "\uD83D\uDE0F", "Smileys & Emotion", "Smirking Face");
        register(":persevere:", "\uD83D\uDE23", "Smileys & Emotion", "Persevering Face");
        register(":disappointed:", "\uD83D\uDE1E", "Smileys & Emotion", "Disappointed Face");
        register(":sweat:", "\uD83D\uDE13", "Smileys & Emotion", "Cold Sweat");
        register(":weary:", "\uD83D\uDE29", "Smileys & Emotion", "Weary Face");
        register(":tired:", "\uD83D\uDE2B", "Smileys & Emotion", "Tired Face");
        register(":sob:", "\uD83D\uDE2D", "Smileys & Emotion", "Loudly Crying Face");
        register(":cry:", "\uD83D\uDE22", "Smileys & Emotion", "Crying Face");
        register(":anguished:", "\uD83D\uDE27", "Smileys & Emotion", "Anguished Face");
        register(":fearful:", "\uD83D\uDE28", "Smileys & Emotion", "Fearful Face");
        register(":worried:", "\uD83D\uDE1F", "Smileys & Emotion", "Worried Face");
        register(":flushed:", "\uD83D\uDE33", "Smileys & Emotion", "Flushed Face");
        register(":astonished:", "\uD83D\uDE32", "Smileys & Emotion", "Astonished Face");
        register(":scream:", "\uD83D\uDE31", "Smileys & Emotion", "Screaming in Fear");
        register(":angry:", "\uD83D\uDE20", "Smileys & Emotion", "Angry Face");
        register(":rage:", "\uD83D\uDE21", "Smileys & Emotion", "Pouting Face");
        register(":triumph:", "\uD83D\uDE24", "Smileys & Emotion", "Face with Look of Triumph");
        register(":sleepy:", "\uD83D\uDE2A", "Smileys & Emotion", "Sleepy Face");
        register(":sleeping:", "\uD83D\uDE34", "Smileys & Emotion", "Sleeping Face");
        register(":dizzy:", "\uD83D\uDE35", "Smileys & Emotion", "Dizzy Face");
        register(":sunglasses:", "\uD83D\uDE0E", "Smileys & Emotion", "Smiling Face with Sunglasses");
        register(":drool:", "\uD83E\uDD24", "Smileys & Emotion", "Drooling Face");
        register(":woozy:", "\uD83E\uDD74", "Smileys & Emotion", "Woozy Face");
        register(":exploding_head:", "\uD83E\uDD2F", "Smileys & Emotion", "Exploding Head");
        register(":cowboy:", "\uD83E\uDD20", "Smileys & Emotion", "Cowboy Hat Face");
        register(":partying:", "\uD83E\uDD73", "Smileys & Emotion", "Partying Face");
        register(":pleading:", "\uD83E\uDD7A", "Smileys & Emotion", "Pleading Face");
        register(":lying:", "\uD83E\uDD25", "Smileys & Emotion", "Lying Face");
        register(":shushing:", "\uD83E\uDD2B", "Smileys & Emotion", "Shushing Face");
        register(":yawning:", "\uD83E\uDD71", "Smileys & Emotion", "Yawning Face");
        register(":mask:", "\uD83D\uDE37", "Smileys & Emotion", "Face with Medical Mask");
        register(":thermometer:", "\uD83E\uDD12", "Smileys & Emotion", "Face with Thermometer");
        register(":head_bandage:", "\uD83E\uDD15", "Smileys & Emotion", "Face with Head-Bandage");
        register(":sick:", "\uD83E\uDD22", "Smileys & Emotion", "Nauseated Face");
        register(":vomit:", "\uD83E\uDD2E", "Smileys & Emotion", "Face with Open Mouth Vomiting");
        register(":sneeze:", "\uD83E\uDD27", "Smileys & Emotion", "Sneezing Face");
        register(":hot:", "\uD83E\uDD75", "Smileys & Emotion", "Hot Face");
        register(":cold:", "\uD83E\uDD76", "Smileys & Emotion", "Cold Face");
        register(":smiling_imp:", "\uD83D\uDE08", "Smileys & Emotion", "Smiling Imp");
        register(":imp:", "\uD83D\uDC7F", "Smileys & Emotion", "Imp");
        register(":skull:", "\uD83D\uDC80", "Smileys & Emotion", "Skull");
        register(":skull_crossbones:", "\u2620\uFE0F", "Smileys & Emotion", "Skull and Crossbones");
        register(":poop:", "\uD83D\uDCA9", "Smileys & Emotion", "Pile of Poo");
        register(":clown:", "\uD83E\uDD21", "Smileys & Emotion", "Clown Face");
        register(":alien:", "\uD83D\uDC7D", "Smileys & Emotion", "Alien");
        register(":robot:", "\uD83E\uDD16", "Smileys & Emotion", "Robot Face");
        register(":ghost:", "\uD83D\uDC7B", "Smileys & Emotion", "Ghost");

        register(":wave:", "\uD83D\uDC4B", "People & Body", "Waving Hand");
        register(":raised_hand:", "\u270B", "People & Body", "Raised Hand");
        register(":ok_hand:", "\uD83D\uDC4C", "People & Body", "OK Hand");
        register(":v:", "\u270C\uFE0F", "People & Body", "Victory Hand");
        register(":crossed_fingers:", "\uD83E\uDD1E", "People & Body", "Crossed Fingers");
        register(":love_you:", "\uD83E\uDD1F", "People & Body", "Love-You Gesture");
        register(":sign_of_horns:", "\uD83E\uDD18", "People & Body", "Sign of the Horns");
        register(":call_me:", "\uD83E\uDD19", "People & Body", "Call Me Hand");
        register(":thumbsup:", "\uD83D\uDC4D", "People & Body", "Thumbs Up");
        register(":thumbsdown:", "\uD83D\uDC4E", "People & Body", "Thumbs Down");
        register(":fist:", "\u270A", "People & Body", "Raised Fist");
        register(":fist_bump:", "\uD83D\uDC4A", "People & Body", "Fist Bump");
        register(":clap:", "\uD83D\uDC4F", "People & Body", "Clapping Hands");
        register(":raised_hands:", "\uD83D\uDE4C", "People & Body", "Raising Hands");
        register(":open_hands:", "\uD83D\uDC50", "People & Body", "Open Hands");
        register(":handshake:", "\uD83E\uDD1D", "People & Body", "Handshake");
        register(":pray:", "\uD83D\uDE4F", "People & Body", "Folded Hands");
        register(":writing_hand:", "\u270D\uFE0F", "People & Body", "Writing Hand");
        register(":nail_care:", "\uD83D\uDC85", "People & Body", "Nail Polish");
        register(":muscle:", "\uD83D\uDCAA", "People & Body", "Flexed Biceps");
        register(":ear:", "\uD83D\uDC42", "People & Body", "Ear");
        register(":nose:", "\uD83D\uDC43", "People & Body", "Nose");
        register(":eyes:", "\uD83D\uDC40", "People & Body", "Eyes");
        register(":eye:", "\uD83D\uDC41\uFE0F", "People & Body", "Eye");
        register(":tongue:", "\uD83D\uDC45", "People & Body", "Tongue");
        register(":lips:", "\uD83D\uDC44", "People & Body", "Mouth");
        register(":brain:", "\uD83E\uDDE0", "People & Body", "Brain");
        register(":heart:", "\u2764\uFE0F", "Symbols", "Red Heart");
        register(":orange_heart:", "\uD83E\uDDE1", "Symbols", "Orange Heart");
        register(":yellow_heart:", "\uD83D\uDC9B", "Symbols", "Yellow Heart");
        register(":green_heart:", "\uD83D\uDC9A", "Symbols", "Green Heart");
        register(":blue_heart:", "\uD83D\uDC99", "Symbols", "Blue Heart");
        register(":purple_heart:", "\uD83D\uDC9C", "Symbols", "Purple Heart");
        register(":black_heart:", "\uD83D\uDDA4", "Symbols", "Black Heart");
        register(":white_heart:", "\uD83E\uDD0D", "Symbols", "White Heart");
        register(":broken_heart:", "\uD83D\uDC94", "Symbols", "Broken Heart");
        register(":heart_exclamation:", "\u2763\uFE0F", "Symbols", "Heart Exclamation");
        register(":two_hearts:", "\uD83D\uDC95", "Symbols", "Two Hearts");
        register(":sparkling_heart:", "\uD83D\uDC96", "Symbols", "Sparkling Heart");
        register(":heartpulse:", "\uD83D\uDC97", "Symbols", "Growing Heart");
        register(":cupid:", "\uD83D\uDC98", "Symbols", "Heart with Arrow");
        register(":gift_heart:", "\uD83D\uDC9D", "Symbols", "Heart with Ribbon");
        register(":revolving_hearts:", "\uD83D\uDC9E", "Symbols", "Revolving Hearts");
        register(":heartbeat:", "\uD83D\uDC93", "Symbols", "Beating Heart");
        register(":kiss:", "\uD83D\uDC8B", "Symbols", "Kiss Mark");
        register(":100:", "\uD83D\uDCAF", "Symbols", "Hundred Points");
        register(":fire:", "\uD83D\uDD25", "Symbols", "Fire");
        register(":sparkles:", "\u2728", "Symbols", "Sparkles");
        register(":star:", "\u2B50", "Symbols", "Star");
        register(":glowing_star:", "\uD83C\uDF1F", "Symbols", "Glowing Star");
        register(":dizzy_star:", "\uD83D\uDCAB", "Symbols", "Dizzy Symbol");
        register(":boom:", "\uD83D\uDCA5", "Symbols", "Collision");
        register(":exclamation:", "\u2757", "Symbols", "Exclamation Mark");
        register(":question:", "\u2753", "Symbols", "Question Mark");
        register(":white_check_mark:", "\u2705", "Symbols", "White Heavy Check Mark");
        register(":x:", "\u274C", "Symbols", "Cross Mark");
        register(":zzz:", "\uD83D\uDCA4", "Symbols", "Sleeping Symbol");
        register(":musical_note:", "\uD83C\uDFB5", "Symbols", "Musical Note");
        register(":notes:", "\uD83C\uDFB6", "Symbols", "Multiple Musical Notes");
        register(":radioactive:", "\u2622\uFE0F", "Symbols", "Radioactive");
        register(":biohazard:", "\u2623\uFE0F", "Symbols", "Biohazard");
        register(":warning:", "\u26A0\uFE0F", "Symbols", "Warning");
        register(":no_entry:", "\u26D4", "Symbols", "No Entry");
        register(":tm:", "\u2122\uFE0F", "Symbols", "Trade Mark");
        register(":copyright:", "\u00A9\uFE0F", "Symbols", "Copyright");
        register(":registered:", "\u00AE\uFE0F", "Symbols", "Registered");
        register(":atm:", "\uD83C\uDFE7", "Symbols", "ATM Sign");
        register(":wc:", "\uD83D\uDEBE", "Symbols", "Water Closet");
        register(":potable_water:", "\uD83D\uDEB0", "Symbols", "Potable Water");
        register(":restroom:", "\uD83D\uDEBB", "Symbols", "Restroom");
        register(":mens:", "\uD83D\uDEB9", "Symbols", "Men's Room");
        register(":womens:", "\uD83D\uDEBA", "Symbols", "Women's Room");
        register(":smoking:", "\uD83D\uDEAC", "Symbols", "Smoking");
        register(":no_smoking:", "\uD83D\uDEAD", "Symbols", "No Smoking");
        register(":recycle:", "\u267B\uFE0F", "Symbols", "Recycling Symbol");

        register(":dog:", "\uD83D\uDC36", "Animals & Nature", "Dog Face");
        register(":cat:", "\uD83D\uDC31", "Animals & Nature", "Cat Face");
        register(":mouse_face:", "\uD83D\uDC2D", "Animals & Nature", "Mouse Face");
        register(":hamster:", "\uD83D\uDC39", "Animals & Nature", "Hamster Face");
        register(":rabbit:", "\uD83D\uDC30", "Animals & Nature", "Rabbit Face");
        register(":fox:", "\uD83E\uDD8A", "Animals & Nature", "Fox Face");
        register(":bear:", "\uD83D\uDC3B", "Animals & Nature", "Bear Face");
        register(":panda:", "\uD83D\uDC3C", "Animals & Nature", "Panda Face");
        register(":koala:", "\uD83D\uDC28", "Animals & Nature", "Koala");
        register(":tiger:", "\uD83D\uDC2F", "Animals & Nature", "Tiger Face");
        register(":lion:", "\uD83E\uDD81", "Animals & Nature", "Lion Face");
        register(":cow:", "\uD83D\uDC2E", "Animals & Nature", "Cow Face");
        register(":pig:", "\uD83D\uDC37", "Animals & Nature", "Pig Face");
        register(":frog:", "\uD83D\uDC38", "Animals & Nature", "Frog Face");
        register(":monkey:", "\uD83D\uDC35", "Animals & Nature", "Monkey Face");
        register(":monkey_face:", "\uD83D\uDC12", "Animals & Nature", "Monkey");
        register(":chicken:", "\uD83D\uDC14", "Animals & Nature", "Chicken");
        register(":penguin:", "\uD83D\uDC27", "Animals & Nature", "Penguin");
        register(":bird:", "\uD83D\uDC26", "Animals & Nature", "Bird");
        register(":eagle:", "\uD83E\uDD85", "Animals & Nature", "Eagle");
        register(":duck:", "\uD83E\uDD86", "Animals & Nature", "Duck");
        register(":owl:", "\uD83E\uDD89", "Animals & Nature", "Owl");
        register(":bat:", "\uD83E\uDD87", "Animals & Nature", "Bat");
        register(":wolf:", "\uD83D\uDC3A", "Animals & Nature", "Wolf Face");
        register(":horse:", "\uD83D\uDC34", "Animals & Nature", "Horse Face");
        register(":unicorn:", "\uD83E\uDD84", "Animals & Nature", "Unicorn Face");
        register(":bee:", "\uD83D\uDC1D", "Animals & Nature", "Honeybee");
        register(":bug:", "\uD83D\uDC1B", "Animals & Nature", "Bug");
        register(":butterfly:", "\uD83E\uDD8B", "Animals & Nature", "Butterfly");
        register(":snail:", "\uD83D\uDC0C", "Animals & Nature", "Snail");
        register(":turtle:", "\uD83D\uDC22", "Animals & Nature", "Turtle");
        register(":snake:", "\uD83D\uDC0D", "Animals & Nature", "Snake");
        register(":dragon:", "\uD83D\uDC09", "Animals & Nature", "Dragon");
        register(":whale:", "\uD83D\uDC33", "Animals & Nature", "Spouting Whale");
        register(":dolphin:", "\uD83D\uDC2C", "Animals & Nature", "Dolphin");
        register(":fish:", "\uD83D\uDC1F", "Animals & Nature", "Fish");
        register(":octopus:", "\uD83D\uDC19", "Animals & Nature", "Octopus");
        register(":crab:", "\uD83E\uDD80", "Animals & Nature", "Crab");
        register(":lizard:", "\uD83E\uDD8E", "Animals & Nature", "Lizard");
        register(":cactus:", "\uD83C\uDF35", "Animals & Nature", "Cactus");
        register(":cherry_blossom:", "\uD83C\uDF38", "Animals & Nature", "Cherry Blossom");
        register(":rose:", "\uD83C\uDF39", "Animals & Nature", "Rose");
        register(":hibiscus:", "\uD83C\uDF3A", "Animals & Nature", "Hibiscus");
        register(":sunflower:", "\uD83C\uDF3B", "Animals & Nature", "Sunflower");
        register(":blossom:", "\uD83C\uDF3C", "Animals & Nature", "Blossom");
        register(":herb:", "\uD83C\uDF3F", "Animals & Nature", "Herb");
        register(":four_leaf_clover:", "\uD83C\uDF40", "Animals & Nature", "Four Leaf Clover");
        register(":maple_leaf:", "\uD83C\uDF41", "Animals & Nature", "Maple Leaf");
        register(":fallen_leaf:", "\uD83C\uDF42", "Animals & Nature", "Fallen Leaf");
        register(":mushroom:", "\uD83C\uDF44", "Animals & Nature", "Mushroom");
        register(":earth:", "\uD83C\uDF0D", "Animals & Nature", "Earth Globe");
        register(":moon:", "\uD83C\uDF19", "Animals & Nature", "Crescent Moon");
        register(":sunny:", "\u2600\uFE0F", "Animals & Nature", "Sun");
        register(":rainbow:", "\uD83C\uDF08", "Animals & Nature", "Rainbow");
        register(":cloud:", "\u2601\uFE0F", "Animals & Nature", "Cloud");
        register(":zap:", "\u26A1", "Animals & Nature", "High Voltage");
        register(":snowflake:", "\u2744\uFE0F", "Animals & Nature", "Snowflake");
        register(":droplet:", "\uD83D\uDCA7", "Animals & Nature", "Droplet");
        register(":ocean:", "\uD83C\uDF0A", "Animals & Nature", "Water Wave");

        register(":apple:", "\uD83C\uDF4E", "Food & Drink", "Red Apple");
        register(":green_apple:", "\uD83C\uDF4F", "Food & Drink", "Green Apple");
        register(":banana:", "\uD83C\uDF4C", "Food & Drink", "Banana");
        register(":grapes:", "\uD83C\uDF47", "Food & Drink", "Grapes");
        register(":watermelon:", "\uD83C\uDF49", "Food & Drink", "Watermelon");
        register(":strawberry:", "\uD83C\uDF53", "Food & Drink", "Strawberry");
        register(":cherries:", "\uD83C\uDF52", "Food & Drink", "Cherries");
        register(":peach:", "\uD83C\uDF51", "Food & Drink", "Peach");
        register(":pizza:", "\uD83C\uDF55", "Food & Drink", "Slice of Pizza");
        register(":hamburger:", "\uD83C\uDF54", "Food & Drink", "Hamburger");
        register(":fries:", "\uD83C\uDF5F", "Food & Drink", "French Fries");
        register(":hotdog:", "\uD83C\uDF2D", "Food & Drink", "Hot Dog");
        register(":taco:", "\uD83C\uDF2E", "Food & Drink", "Taco");
        register(":burrito:", "\uD83C\uDF2F", "Food & Drink", "Burrito");
        register(":sushi:", "\uD83C\uDF63", "Food & Drink", "Sushi");
        register(":rice:", "\uD83C\uDF5A", "Food & Drink", "Cooked Rice");
        register(":ramen:", "\uD83C\uDF5C", "Food & Drink", "Steaming Bowl");
        register(":spaghetti:", "\uD83C\uDF5D", "Food & Drink", "Spaghetti");
        register(":bread:", "\uD83C\uDF5E", "Food & Drink", "Bread");
        register(":cake:", "\uD83C\uDF70", "Food & Drink", "Shortcake");
        register(":cookie:", "\uD83C\uDF6A", "Food & Drink", "Cookie");
        register(":chocolate:", "\uD83C\uDF6B", "Food & Drink", "Chocolate Bar");
        register(":donut:", "\uD83C\uDF69", "Food & Drink", "Doughnut");
        register(":icecream:", "\uD83C\uDF66", "Food & Drink", "Soft Ice Cream");
        register(":beer:", "\uD83C\uDF7A", "Food & Drink", "Beer Mug");
        register(":wine:", "\uD83C\uDF77", "Food & Drink", "Wine Glass");
        register(":cocktail:", "\uD83C\uDF78", "Food & Drink", "Cocktail Glass");
        register(":coffee:", "\u2615", "Food & Drink", "Hot Beverage");
        register(":tea:", "\uD83C\uDF75", "Food & Drink", "Teacup Without Handle");
        register(":sake:", "\uD83C\uDF76", "Food & Drink", "Sake");

        register(":soccer:", "\u26BD", "Activities", "Soccer Ball");
        register(":basketball:", "\uD83C\uDFC0", "Activities", "Basketball");
        register(":football:", "\uD83C\uDFC8", "Activities", "American Football");
        register(":baseball:", "\u26BE", "Activities", "Baseball");
        register(":tennis:", "\uD83C\uDFBE", "Activities", "Tennis");
        register(":volleyball:", "\uD83C\uDFD0", "Activities", "Volleyball");
        register(":golf:", "\u26F3", "Activities", "Flag in Hole");
        register(":trophy:", "\uD83C\uDFC6", "Activities", "Trophy");
        register(":medal:", "\uD83C\uDFC5", "Activities", "Sports Medal");
        register(":gold:", "\uD83E\uDD47", "Activities", "1st Place Medal");
        register(":silver:", "\uD83E\uDD48", "Activities", "2nd Place Medal");
        register(":bronze:", "\uD83E\uDD49", "Activities", "3rd Place Medal");
        register(":game_die:", "\uD83C\uDFB2", "Activities", "Game Die");
        register(":chess:", "\u265F\uFE0F", "Activities", "Chess Pawn");
        register(":art:", "\uD83C\uDFA8", "Activities", "Artist Palette");
        register(":guitar:", "\uD83C\uDFB8", "Activities", "Guitar");
        register(":trumpet:", "\uD83C\uDFBA", "Activities", "Trumpet");
        register(":violin:", "\uD83C\uDFBB", "Activities", "Violin");
        register(":drum:", "\uD83E\uDD41", "Activities", "Drum");
        register(":microphone:", "\uD83C\uDFA4", "Activities", "Microphone");
        register(":headphones:", "\uD83C\uDFA7", "Activities", "Headphone");
        register(":ticket:", "\uD83C\uDFAB", "Activities", "Ticket");
        register(":clapper:", "\uD83C\uDFAC", "Activities", "Clapper Board");
        register(":video_game:", "\uD83C\uDFAE", "Activities", "Video Game");
        register(":dart:", "\uD83C\uDFAF", "Activities", "Direct Hit");
        register(":slot_machine:", "\uD83C\uDFB0", "Activities", "Slot Machine");
        register(":8ball:", "\uD83C\uDFB1", "Activities", "Billards");

        register(":airplane:", "\u2708\uFE0F", "Travel & Places", "Airplane");
        register(":car:", "\uD83D\uDE97", "Travel & Places", "Car");
        register(":taxi:", "\uD83D\uDE95", "Travel & Places", "Taxi");
        register(":bus:", "\uD83D\uDE8C", "Travel & Places", "Bus");
        register(":train:", "\uD83D\uDE86", "Travel & Places", "Train");
        register(":rocket:", "\uD83D\uDE80", "Travel & Places", "Rocket");
        register(":satellite:", "\uD83D\uDEF0\uFE0F", "Travel & Places", "Satellite");
        register(":ship:", "\uD83D\uDEA2", "Travel & Places", "Ship");
        register(":bicycle:", "\uD83D\uDEB2", "Travel & Places", "Bicycle");
        register(":motorcycle:", "\uD83C\uDFCD\uFE0F", "Travel & Places", "Motorcycle");
        register(":house:", "\uD83C\uDFE0", "Travel & Places", "House");
        register(":office:", "\uD83C\uDFE2", "Travel & Places", "Office Building");
        register(":hospital:", "\uD83C\uDFE5", "Travel & Places", "Hospital");
        register(":bank:", "\uD83C\uDFE6", "Travel & Places", "Bank");
        register(":hotel:", "\uD83C\uDFE8", "Travel & Places", "Hotel");
        register(":church:", "\u26EA", "Travel & Places", "Church");
        register(":mosque:", "\uD83D\uDD4C", "Travel & Places", "Mosque");
        register(":castle:", "\uD83C\uDFF0", "Travel & Places", "Castle");
        register(":japan:", "\uD83D\uDDFE", "Travel & Places", "Japanese Castle");
        register(":mount_fuji:", "\uD83D\uDDFB", "Travel & Places", "Mount Fuji");
        register(":beach:", "\uD83C\uDFD6\uFE0F", "Travel & Places", "Beach");
        register(":desert:", "\uD83C\uDFDC\uFE0F", "Travel & Places", "Desert");
        register(":island:", "\uD83C\uDFDD\uFE0F", "Travel & Places", "Desert Island");
        register(":park:", "\uD83C\uDFDE\uFE0F", "Travel & Places", "National Park");
        register(":stadium:", "\uD83C\uDFDF\uFE0F", "Travel & Places", "Stadium");
        register(":statue:", "\uD83D\uDDFD", "Travel & Places", "Statue of Liberty");
        register(":tower:", "\uD83D\uDDFC", "Travel & Places", "Tokyo Tower");

        register(":bulb:", "\uD83D\uDCA1", "Objects", "Light Bulb");
        register(":flashlight:", "\uD83D\uDD26", "Objects", "Flashlight");
        register(":book:", "\uD83D\uDCD6", "Objects", "Open Book");
        register(":newspaper:", "\uD83D\uDCF0", "Objects", "Newspaper");
        register(":computer:", "\uD83D\uDCBB", "Objects", "Personal Computer");
        register(":computer_mouse:", "\uD83D\uDDB1\uFE0F", "Objects", "Computer Mouse"); // already have mouse animal, use different unicode
        register(":keyboard:", "\u2328\uFE0F", "Objects", "Keyboard");
        register(":phone:", "\uD83D\uDCF1", "Objects", "Mobile Phone");
        register(":email:", "\u2709\uFE0F", "Objects", "Envelope");
        register(":inbox:", "\uD83D\uDCE5", "Objects", "Inbox Tray");
        register(":outbox:", "\uD83D\uDCE4", "Objects", "Outbox Tray");
        register(":package:", "\uD83D\uDCE6", "Objects", "Package");
        register(":memo:", "\uD83D\uDCDD", "Objects", "Memo");
        register(":clipboard:", "\uD83D\uDCCB", "Objects", "Clipboard");
        register(":calendar:", "\uD83D\uDCC5", "Objects", "Calendar");
        register(":clock:", "\uD83D\uDD53", "Objects", "Clock");
        register(":alarm:", "\u23F0", "Objects", "Alarm Clock");
        register(":watch:", "\u231A", "Objects", "Watch");
        register(":gear:", "\u2699\uFE0F", "Objects", "Gear");
        register(":wrench:", "\uD83D\uDD27", "Objects", "Wrench");
        register(":hammer:", "\uD83D\uDD28", "Objects", "Hammer");
        register(":tools:", "\uD83D\uDEE0\uFE0F", "Objects", "Hammer and Wrench");
        register(":laptop:", "\uD83D\uDCBB", "Objects", "Laptop");
        register(":camera:", "\uD83D\uDCF7", "Objects", "Camera");
        register(":video:", "\uD83D\uDCF9", "Objects", "Video Camera");
        register(":tv:", "\uD83D\uDCFA", "Objects", "Television");
        register(":radio:", "\uD83D\uDCFB", "Objects", "Radio");
        register(":speaker:", "\uD83D\uDD0A", "Objects", "Speaker");
        register(":bell:", "\uD83D\uDD14", "Objects", "Bell");
        register(":no_bell:", "\uD83D\uDD15", "Objects", "Bell with Slash");
        register(":megaphone:", "\uD83D\uDCE3", "Objects", "Megaphone");
        register(":loudspeaker:", "\uD83D\uDCE2", "Objects", "Loudspeaker");
        register(":key:", "\uD83D\uDD11", "Objects", "Key");
        register(":lock:", "\uD83D\uDD12", "Objects", "Lock");
        register(":unlock:", "\uD83D\uDD13", "Objects", "Open Lock");
        register(":magnifying_glass:", "\uD83D\uDD0E", "Objects", "Magnifying Glass Tilted Right");
        register(":link:", "\uD83D\uDD17", "Objects", "Link");
        register(":scissors:", "\u2702\uFE0F", "Objects", "Scissors");
        register(":bomb:", "\uD83D\uDCA3", "Objects", "Bomb");
        register(":syringe:", "\uD83D\uDC89", "Objects", "Syringe");
        register(":pill:", "\uD83D\uDC8A", "Objects", "Pill");
        register(":moneybag:", "\uD83D\uDCB0", "Objects", "Money Bag");
        register(":dollar:", "\uD83D\uDCB5", "Objects", "Dollar Banknote");
        register(":credit_card:", "\uD83D\uDCB3", "Objects", "Credit Card");
        register(":chart:", "\uD83D\uDCCA", "Objects", "Bar Chart");
        register(":gem:", "\uD83D\uDC8E", "Objects", "Gem Stone");
        register(":gift:", "\uD83C\uDF81", "Objects", "Wrapped Gift");
        register(":balloon:", "\uD83C\uDF88", "Objects", "Balloon");
        register(":tada:", "\uD83C\uDF89", "Objects", "Party Popper");
        register(":confetti:", "\uD83C\uDF8A", "Objects", "Confetti Ball");
        register(":crown:", "\uD83D\uDC51", "Objects", "Crown");

        register(":flag_white:", "\uD83C\uDFF3\uFE0F", "Flags", "White Flag");
        register(":flag_black:", "\uD83C\uDFF4", "Flags", "Black Flag");
        register(":rainbow_flag:", "\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08", "Flags", "Rainbow Flag");
        register(":checkered_flag:", "\uD83C\uDFC1", "Flags", "Chequered Flag");
        register(":triangular_flag:", "\uD83D\uDEA9", "Flags", "Triangular Flag");
        register(":crossed_flags:", "\uD83C\uDF8C", "Flags", "Crossed Flags");
        register(":pirate_flag:", "\uD83C\uDFF4\u200D\u2620\uFE0F", "Flags", "Pirate Flag");

        int pua = PUA_START;
        for (EmojiEntry e : ALL) {
            PUA_BY_SHORTCODE.put(e.shortcode(), new String(Character.toChars(pua)));
            int firstCp = e.unicode().codePointAt(0);
            UNICODE_FIRST_CP_TO_PUA.put(firstCp, new String(Character.toChars(pua)));
            EMOJI_FIRST_CODEPOINTS.add(firstCp);
            pua++;
        }

        for (EmojiEntry e : ALL) {
            String puaStr = PUA_BY_SHORTCODE.get(e.shortcode());
            String uni = e.unicode();
            int first = uni.codePointAt(0);
            UNICODE_CANDIDATES.computeIfAbsent(first, k -> new ArrayList<>()).add(new UnicodeEntry(uni, puaStr));
            String stripped = uni.replace("\uFE0F", "");
            if (!stripped.equals(uni)) {
                int sf = stripped.codePointAt(0);
                UNICODE_CANDIDATES.computeIfAbsent(sf, k -> new ArrayList<>()).add(new UnicodeEntry(stripped, puaStr));
            }
        }
        for (List<UnicodeEntry> list : UNICODE_CANDIDATES.values()) {
            list.sort(Comparator.comparingInt((UnicodeEntry u) -> u.unicode().length()).reversed());
        }
    }

    private static void register(String shortcode, String unicode, String category, String name) {
        EmojiEntry entry = new EmojiEntry(shortcode, unicode, category, name);
        ALL.add(entry);
        BY_SHORTCODE.put(shortcode, entry);
        BY_CHAR.put(unicode, shortcode);
        BY_CATEGORY.computeIfAbsent(category, k -> {
            CATEGORIES.add(category);
            return new ArrayList<>();
        }).add(entry);
    }

    public static List<EmojiEntry> getAll() {
        return ALL;
    }

    public static EmojiEntry byShortcode(String shortcode) {
        return BY_SHORTCODE.get(shortcode);
    }

    public static List<EmojiEntry> byCategory(String category) {
        return BY_CATEGORY.getOrDefault(category, List.of());
    }

    public static List<String> getCategories() {
        return CATEGORIES;
    }

    public static List<EmojiEntry> search(String query) {
        if (query == null || query.isEmpty()) return ALL;
        String lower = query.toLowerCase();
        return ALL.stream()
                .filter(e -> e.shortcode().toLowerCase().contains(lower)
                        || e.name().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    public static String categoryLangKey(String category) {
        return CATEGORY_LANG_KEYS.getOrDefault(category, category);
    }

    public static String replaceShortcodes(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuffer sb = new StringBuffer();
        Matcher m = SHORTCODE_PATTERN.matcher(text);
        while (m.find()) {
            String pua = PUA_BY_SHORTCODE.get(m.group());
            m.appendReplacement(sb, pua != null ? Matcher.quoteReplacement(pua) : m.group());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String puaChar(String shortcode) {
        return PUA_BY_SHORTCODE.get(shortcode);
    }

    public static String puaChar(EmojiEntry entry) {
        return PUA_BY_SHORTCODE.get(entry.shortcode());
    }

    public static int indexOf(String shortcode) {
        EmojiEntry e = BY_SHORTCODE.get(shortcode);
        return e != null ? ALL.indexOf(e) : -1;
    }

    public static int indexOf(EmojiEntry entry) {
        return ALL.indexOf(entry);
    }

    public static final ResourceLocation EMOJI_FONT = ResourceLocation.fromNamespaceAndPath("chatsphere", "emoji");
    public static final Style EMOJI_STYLE = Style.EMPTY.withFont(EMOJI_FONT).withColor(ChatFormatting.WHITE);

    public static int puaStart() { return PUA_START; }

    private static UnicodeEntry matchUnicodeEntry(String text, int pos) {
        int cp = text.codePointAt(pos);
        List<UnicodeEntry> candidates = UNICODE_CANDIDATES.get(cp);
        if (candidates != null) {
            for (UnicodeEntry ue : candidates) {
                if (text.startsWith(ue.unicode(), pos)) return ue;
            }
        }
        return null;
    }

    public static boolean containsPua(String text) {
        if (text == null) return false;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (cp >= PUA_START && cp <= PUA_START + ALL.size()) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    public static boolean containsPua(Component component) {
        return containsPua(component.getString());
    }

    public static boolean isEmojiCodepoint(int cp) {
        return EMOJI_FIRST_CODEPOINTS.contains(cp);
    }

    public static String unicodeFirstCpToPua(int cp) {
        return UNICODE_FIRST_CP_TO_PUA.get(cp);
    }

    public static final int EMOJI_Y_OFFSET = 2;

    private static int emojiRenderOffsetY() {
        return EMOJI_Y_OFFSET;
    }

    public static MutableComponent toComponent(String text) {
        if (text == null || text.isEmpty()) return Component.literal("");
        MutableComponent result = null;
        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            boolean isPua = cp >= PUA_START && cp <= PUA_START + ALL.size();
            UnicodeEntry ue = isPua ? null : matchUnicodeEntry(text, i);
            if (isPua) {
                String puaStr = text.substring(i, i + charCount);
                if (result == null) {
                    result = Component.literal(puaStr).withStyle(EMOJI_STYLE);
                } else {
                    result.append(Component.literal(puaStr).withStyle(EMOJI_STYLE));
                }
                i += charCount;
            } else if (ue != null) {
                if (result == null) {
                    result = Component.literal(ue.pua()).withStyle(EMOJI_STYLE);
                } else {
                    result.append(Component.literal(ue.pua()).withStyle(EMOJI_STYLE));
                }
                i += ue.unicode().length();
            } else {
                if (result == null) {
                    result = Component.literal(text.substring(i, i + charCount));
                } else {
                    result.append(Component.literal(text.substring(i, i + charCount)));
                }
                i += charCount;
            }
        }
        return result != null ? result : Component.literal("");
    }

    public static FormattedCharSequence toFormattedCharSequence(String text) {
        if (text == null || text.isEmpty()) return FormattedCharSequence.EMPTY;
        String withPua = replaceShortcodes(text);
        List<FormattedCharSequence> parts = new ArrayList<>();
        int i = 0;
        int len = withPua.length();
        while (i < len) {
            int cp = withPua.codePointAt(i);
            int charCount = Character.charCount(cp);
            boolean isPua = cp >= PUA_START && cp <= PUA_START + ALL.size();
            UnicodeEntry ue = isPua ? null : matchUnicodeEntry(withPua, i);
            if (isPua) {
                parts.add(FormattedCharSequence.forward(withPua.substring(i, i + charCount), EMOJI_STYLE));
                i += charCount;
            } else if (ue != null) {
                parts.add(FormattedCharSequence.forward(ue.pua(), EMOJI_STYLE));
                i += ue.unicode().length();
            } else {
                int start = i;
                while (i < len) {
                    cp = withPua.codePointAt(i);
                    charCount = Character.charCount(cp);
                    boolean isPuaInner = cp >= PUA_START && cp <= PUA_START + ALL.size();
                    if (isPuaInner || matchUnicodeEntry(withPua, i) != null) break;
                    i += charCount;
                }
                parts.add(FormattedCharSequence.forward(withPua.substring(start, i), Style.EMPTY));
            }
        }
        return FormattedCharSequence.composite(parts);
    }

    public static String shortcodesToUnicode(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuffer sb = new StringBuffer();
        Matcher m = SHORTCODE_PATTERN.matcher(text);
        while (m.find()) {
            EmojiEntry entry = BY_SHORTCODE.get(m.group());
            String replacement = entry != null ? entry.unicode() : m.group();
            replacement = replacement.replace("\uFE0F", "");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
