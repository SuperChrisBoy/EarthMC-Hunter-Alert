package net.earthmc.hunteralert.data;

import java.util.Locale;

public final class ServerIdentity {
    private ServerIdentity() {}
    public static boolean isEarthMc(String address) {
        if(address==null) return false;
        String host=address.toLowerCase(Locale.ROOT).split(":",2)[0];
        if(host.endsWith(".")) host=host.substring(0,host.length()-1);
        return host.equals("earthmc.net") || host.endsWith(".earthmc.net");
    }
}
