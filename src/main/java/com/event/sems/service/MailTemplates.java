package com.event.sems.service;

final class MailTemplates {
  private MailTemplates() {}

  static String message(String title, String body, String highlight, String actionLabel,
      String actionUrl, String note) {
    String emphasized = highlight == null ? "" :
        "<p style=\"margin:24px 0;padding:16px;background:#f4f6fa;border-radius:8px;"
            + "font-size:22px;font-weight:bold;letter-spacing:2px;text-align:center\">"
            + escape(highlight) + "</p>";
    String action = actionUrl == null ? "" :
        "<p style=\"margin:24px 0\"><a href=\"" + escape(actionUrl) + "\" "
            + "style=\"display:inline-block;padding:13px 20px;background:#d31932;color:#fff;"
            + "text-decoration:none;border-radius:8px;font-weight:bold\">"
            + escape(actionLabel) + "</a></p>";
    return "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
        + "<title>" + escape(title) + "</title></head>"
        + "<body style=\"margin:0;padding:24px;background:#f4f6fa;"
        + "font-family:Arial,sans-serif;color:#17213a\">"
        + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
        + "style=\"max-width:600px;width:100%;margin:auto;background:#fff;"
        + "border:1px solid #e2e7f0;border-radius:14px\">"
        + "<tr><td style=\"padding:28px 32px;border-top:6px solid #d31932\">"
        + "<p style=\"margin:0 0 8px;color:#d31932;font-size:13px;"
        + "font-weight:bold;letter-spacing:2px\">SEMS · THÔNG BÁO</p>"
        + "<h1 style=\"margin:0 0 18px;font-size:25px;line-height:1.3\">"
        + escape(title) + "</h1>"
        + "<p style=\"line-height:1.6\">" + escape(body).replace("\n", "<br>") + "</p>"
        + emphasized + action
        + "<p style=\"margin:24px 0 0;color:#60708c;font-size:13px;line-height:1.5\">"
        + escape(note) + "</p></td></tr></table></body></html>";
  }

  static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }
}
