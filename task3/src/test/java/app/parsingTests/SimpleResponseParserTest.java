package app.parsingTests;

import app.parsing.ParsedResponse;
import app.parsing.ResponseParser;
import app.parsing.SimpleResponseParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimpleResponseParserTest {
    private final ResponseParser parser = new SimpleResponseParser();

    @Test
    void testParseValidJson() {
        String json = "{\"message\":\"Hello\",\"successors\":[\"123\",\"456\"]}";
        ParsedResponse result = parser.parse(json);

        assertEquals("Hello", result.message());
        assertEquals(List.of("123", "456"), result.successors());
    }

    @Test
    void testParseEmptySuccessors() {
        String json = "{\"message\":\"End node\",\"successors\":[]}";
        ParsedResponse result = parser.parse(json);

        assertEquals("End node", result.message());
        assertTrue(result.successors().isEmpty());
    }
}