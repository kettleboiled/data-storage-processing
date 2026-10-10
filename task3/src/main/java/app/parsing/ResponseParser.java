package app.parsing;

public interface ResponseParser {

    ParsedResponse parse(String json);
}