package net.earthmc.hunteralert;

import net.earthmc.hunteralert.api.*;
import net.earthmc.hunteralert.model.Models.Feed;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FeedValidationTest {
    @Test void malformedFeedNeverBecomesHealthyEmptyFeed() {
        assertFalse(FeedValidation.valid(null)); assertFalse(FeedValidation.valid("<html>error</html>"));
        assertFalse(FeedValidation.valid("{}")); assertFalse(FeedValidation.valid("{\"players\":null}"));
        assertFalse(FeedValidation.valid("[{\"name\":\"Hunter\"}]"));
        assertTrue(FeedValidation.valid("{\"players\":[]}"));
        assertTrue(FeedValidation.valid("[{\"name\":\"Hunter\",\"world\":\"terra\",\"position\":{\"x\":1,\"z\":2}}]"));
        assertTrue(FeedValidation.valid("[{\"hidden\":true}]"));
    }
    @Test void lateResponseFromOldConnectionIsDiscarded() {
        EmcMapAddonBridge.reset(); long generation=EmcMapAddonBridge.generation();
        EmcMapAddonBridge.reset(); EmcMapAddonBridge.publish(new Feed(1000,0,true,List.of()),generation);
        assertFalse(EmcMapAddonBridge.latest().healthy());
    }
}
