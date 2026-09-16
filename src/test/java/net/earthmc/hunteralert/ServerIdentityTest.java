package net.earthmc.hunteralert;

import net.earthmc.hunteralert.data.ServerIdentity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerIdentityTest {
    @Test void exactDomainAndSubdomainsOnly() {
        for(String host:new String[]{"earthmc.net","play.earthmc.net:25565","PLAY.EARTHMC.NET","earthmc.net."}) assertTrue(ServerIdentity.isEarthMc(host),host);
        for(String host:new String[]{"earthmc.net.attacker.example","fakeearthmc.net","earth.example","localhost","127.0.0.1",""}) assertFalse(ServerIdentity.isEarthMc(host),host);
        assertFalse(ServerIdentity.isEarthMc(null));
    }
}
