package app.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleResponseParser implements ResponseParser {
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SUCCESSORS_PATTERN = Pattern.compile("\"successors\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern ITEM_PATTERN = Pattern.compile("\"([^\"]*)\"");

    @Override
    public ParsedResponse parse(String json) {
        String message = null;
        Matcher msgMatcher = MESSAGE_PATTERN.matcher(json);
        if (msgMatcher.find()) {
            message = msgMatcher.group(1);
        }

        List<String> successors = new ArrayList<>();
        Matcher succMatcher = SUCCESSORS_PATTERN.matcher(json);
        if (succMatcher.find()) {
            String itemsBlock = succMatcher.group(1);
            Matcher itemMatcher = ITEM_PATTERN.matcher(itemsBlock);
            while (itemMatcher.find()) {
                successors.add(itemMatcher.group(1));
            }
        }

        return new ParsedResponse(message, successors);
    }
}