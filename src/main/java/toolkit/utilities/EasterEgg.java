package toolkit.utilities;

import toolkit.configurations.Config;
import toolkit.configurations.ApplicationFlags;
import toolkit.windows.Toolkit;

public class EasterEgg {
    public static void initialize(Toolkit toolkit) {
        toolkit.debugMenu.setVisible(false);

        String username = System.getProperty("user.name").toLowerCase();
        
        toolkit.debugMenu.setVisible(Config.instance.getCurrentProfile().debug);
        
        if (username.equals("veryc")) {
            toolkit.debugMenu.setVisible(true);
            toolkit.setTitle("VeryCoolMe's Modding Emporium");
        }
        
        if (username.equals("manoplay")) {
            toolkit.debugMenu.setVisible(true);
        }

        if (username.equals("elija")) {
            toolkit.setTitle("Eli's Den of Cancer");
            toolkit.debugMenu.setVisible(true);
        }

        if (username.equals("dominick")) {
            toolkit.setTitle("Undertale Piracy Tool");
            toolkit.debugMenu.setVisible(true);
        }

        if (username.equals("rueezus")) {
            toolkit.setTitle("Overture");
            toolkit.debugMenu.setVisible(true);
        }

        if (username.equals("joele")) {
            toolkit.setTitle("Acrosnus Toolkit");
            toolkit.debugMenu.setVisible(true);
        }

        if (username.equals("etleg") || username.equals("eddie")) {
            toolkit.setTitle("GregTool");
            toolkit.debugMenu.setVisible(true);
        }

        if (username.equals("madbrine")) {
            toolkit.setTitle("farctool3");
            toolkit.debugMenu.setVisible(true);
        }

        if (toolkit.debugMenu.isVisible()) {
            toolkit.setTitle(toolkit.getTitle() + " | Debug");
        }
    }
}
