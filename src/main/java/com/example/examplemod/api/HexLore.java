//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.examplemod.api;

public final class HexLore {
    private HexLore() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return null;
        } else {
            String s = input.trim();
            if (s.startsWith("§#")) {
                s = s.substring(1);
            }

            if (!s.startsWith("#")) {
                s = "#" + s;
            }

            if (s.matches("(?i)#([0-9a-f]{3})")) {
                String h = s.substring(1).toUpperCase();
                return "#" + dup(h.charAt(0)) + dup(h.charAt(1)) + dup(h.charAt(2));
            } else {
                return s.matches("(?i)#([0-9a-f]{6})") ? s.toUpperCase() : null;
            }
        }
    }

    private static String dup(char c) {
        return "" + c + c;
    }

    public static String toControl(String hex, String text) {
        String n = normalize(hex);
        if (n == null) {
            throw new IllegalArgumentException("Bad hex: " + hex);
        } else {
            return "§#" + n.substring(1) + (text == null ? "" : text) + "§r";
        }
    }

    public static String toTag(String hex, String text) {
        String n = normalize(hex);
        if (n == null) {
            throw new IllegalArgumentException("Bad hex: " + hex);
        } else {
            return "<" + n + ">" + (text == null ? "" : text) + "</#>";
        }
    }

    public static String gradient(String startHex, String endHex, String text) {
        String s = normalize(startHex);
        String e = normalize(endHex);
        if (s != null && e != null) {
            if (text != null && !text.isEmpty()) {
                int n = text.length();
                int r1 = Integer.parseInt(s.substring(1, 3), 16);
                int g1 = Integer.parseInt(s.substring(3, 5), 16);
                int b1 = Integer.parseInt(s.substring(5, 7), 16);
                int r2 = Integer.parseInt(e.substring(1, 3), 16);
                int g2 = Integer.parseInt(e.substring(3, 5), 16);
                int b2 = Integer.parseInt(e.substring(5, 7), 16);
                StringBuilder out = new StringBuilder(n * 12);

                for(int i = 0; i < n; ++i) {
                    double t = n == 1 ? (double)0.0F : (double)i / (double)(n - 1);
                    int r = (int)Math.round((double)r1 + (double)(r2 - r1) * t);
                    int g = (int)Math.round((double)g1 + (double)(g2 - g1) * t);
                    int b = (int)Math.round((double)b1 + (double)(b2 - b1) * t);
                    out.append("§#").append(h2(r)).append(h2(g)).append(h2(b)).append(text.charAt(i));
                }

                out.append("§r");
                return out.toString();
            } else {
                return "";
            }
        } else {
            throw new IllegalArgumentException("Bad gradient hex");
        }
    }

    public static String rainbow(String text) {
        if (text != null && !text.isEmpty()) {
            int n = text.length();
            StringBuilder out = new StringBuilder(n * 12);

            for(int i = 0; i < n; ++i) {
                double h = (double)i / (double)Math.max(1, n);
                int rgb = hsvToRgb(h, (double)1.0F, (double)1.0F);
                out.append("§#").append(h2(rgb >> 16 & 255)).append(h2(rgb >> 8 & 255)).append(h2(rgb & 255)).append(text.charAt(i));
            }

            out.append("§r");
            return out.toString();
        } else {
            return "";
        }
    }

    private static int hsvToRgb(double h, double s, double v) {
        double c = v * s;
        double x = c * ((double)1.0F - Math.abs(h * (double)6.0F % (double)2.0F - (double)1.0F));
        double m = v - c;
        double r = (double)0.0F;
        double g = (double)0.0F;
        double b = (double)0.0F;
        int seg = (int)(h * (double)6.0F);
        switch (seg) {
            case 0:
                r = c;
                g = x;
                b = (double)0.0F;
                break;
            case 1:
                r = x;
                g = c;
                b = (double)0.0F;
                break;
            case 2:
                r = (double)0.0F;
                g = c;
                b = x;
                break;
            case 3:
                r = (double)0.0F;
                g = x;
                b = c;
                break;
            case 4:
                r = x;
                g = (double)0.0F;
                b = c;
                break;
            default:
                r = c;
                g = (double)0.0F;
                b = x;
        }

        return (int)((r + m) * (double)255.0F) << 16 | (int)((g + m) * (double)255.0F) << 8 | (int)((b + m) * (double)255.0F);
    }

    private static String h2(int v) {
        String s = Integer.toHexString(v & 255).toUpperCase();
        return s.length() == 1 ? "0" + s : s;
    }
}
