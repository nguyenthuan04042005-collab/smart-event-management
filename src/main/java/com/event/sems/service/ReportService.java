package com.event.sems.service;

import com.event.sems.common.ApiException;
import com.event.sems.entity.Event;
import com.event.sems.repository.CheckinLogRepository;
import com.event.sems.repository.EventRepository;
import com.event.sems.repository.FeedbackRepository;
import com.event.sems.repository.TicketRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private final EventRepository events;
  private final TicketRepository tickets;
  private final CheckinLogRepository checkins;
  private final FeedbackRepository feedback;
  private final FeedbackService feedbackService;

  @Transactional(readOnly = true)
  public byte[] feedbackExcel(long eventId) {
    Event event = events.findById(eventId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    var summary = feedbackService.summary(eventId);
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      CellStyle heading = workbook.createCellStyle();
      heading.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
      heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      Font font = workbook.createFont();
      font.setBold(true);
      font.setColor(IndexedColors.WHITE.getIndex());
      heading.setFont(font);
      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

      Sheet overview = workbook.createSheet("Tổng quan");
      addRow(overview, 0, heading, "Báo cáo đánh giá sự kiện");
      addRow(overview, 2, null, "Sự kiện", event.getTitle());
      addRow(overview, 3, null, "Tổng lượt đánh giá", summary.count());
      addRow(overview, 4, null, "Điểm trung bình / 5", summary.averageRating());
      addRow(overview, 6, heading, "Số sao", "Số lượt", "Tỷ lệ (%)");
      int rowNumber = 7;
      for (var rating : summary.ratings())
        addRow(overview, rowNumber++, null, rating.rating(), rating.count(), rating.percentage());
      overview.setColumnWidth(0, 6500);
      overview.setColumnWidth(1, 10500);
      overview.setColumnWidth(2, 4500);

      Sheet details = workbook.createSheet("Chi tiết");
      addRow(details, 0, heading, "Thời điểm", "Người đánh giá", "Số sao", "Nhận xét");
      int detailRow = 1;
      for (var item : feedback.findByEventIdOrderByIdDesc(eventId)) {
        addRow(details, detailRow, null, item.getCreatedAt(),
            item.getTicket().getAttendee().getFullName(), item.getRating(),
            item.getCommentText());
        details.getRow(detailRow++).getCell(0).setCellStyle(dateStyle);
      }
      details.createFreezePane(0, 1);
      details.setColumnWidth(0, 5500);
      details.setColumnWidth(1, 7500);
      details.setColumnWidth(2, 3000);
      details.setColumnWidth(3, 18000);
      if (detailRow > 1) details.setAutoFilter(
          new org.apache.poi.ss.util.CellRangeAddress(0, detailRow - 1, 0, 3));

      workbook.write(output);
      return output.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("Không tạo được báo cáo Excel.", error);
    }
  }

  private static void addRow(Sheet sheet, int index, CellStyle style, Object... values) {
    Row row = sheet.createRow(index);
    for (int column = 0; column < values.length; column++) {
      var cell = row.createCell(column);
      Object value = values[column];
      if (value instanceof Number number) cell.setCellValue(number.doubleValue());
      else if (value instanceof LocalDateTime time) cell.setCellValue(time);
      else cell.setCellValue(value == null ? "" : value.toString());
      if (style != null) cell.setCellStyle(style);
    }
  }

  @Transactional(readOnly = true)
  public byte[] csv(long eventId, String type) {
    Event event =
        events
            .findById(eventId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."));
    List<String[]> rows = new ArrayList<>();
    switch (type) {
      case "registrations" -> {
        rows.add(
            new String[] {
              "Sự kiện", "Họ tên", "Email", "Đơn vị", "Chức vụ / vai trò", "Mã vé", "Trạng thái", "Ngày cấp", "Giờ check-in", "Ngày hủy"
            });
        for (var ticket : tickets.findByEventIdOrderByIdDesc(eventId)) {
          rows.add(
              new String[] {
                event.getTitle(), ticket.getAttendee().getFullName(),
                    ticket.getAttendee().getEmail(),
                ticket.getAttendee().getOrganization(),
                ticket.getAttendee().getPositionTitle(),
                ticket.getCode(), ticket.getStatus(), date(ticket.getIssuedAt()),
                date(ticket.getCheckedInAt()), date(ticket.getCancelledAt())
              });
        }
      }
      case "checkins" -> {
        rows.add(
            new String[] {
              "Sự kiện", "Họ tên", "Email", "Mã vé", "Phương thức", "Nhân viên", "Giờ check-in"
            });
        for (var log : checkins.findByEventIdOrderByCheckedInAtAsc(eventId)) {
          var ticket = log.getTicket();
          rows.add(
              new String[] {
                event.getTitle(),
                ticket.getAttendee().getFullName(),
                ticket.getAttendee().getEmail(),
                ticket.getCode(),
                log.getMethod(),
                log.getOperatorName(),
                date(log.getCheckedInAt())
              });
        }
      }
      case "feedback" -> {
        rows.add(
            new String[] {
              "Sự kiện", "Họ tên", "Email", "Mã vé", "Điểm", "Nhận xét", "Ngày đánh giá"
            });
        for (var item : feedback.findByEventIdOrderByIdDesc(eventId)) {
          var ticket = item.getTicket();
          rows.add(
              new String[] {
                event.getTitle(), ticket.getAttendee().getFullName(),
                    ticket.getAttendee().getEmail(),
                ticket.getCode(), String.valueOf(item.getRating()), item.getCommentText(),
                date(item.getCreatedAt())
              });
        }
      }
      default -> throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy loại báo cáo.");
    }
    StringBuilder csv = new StringBuilder("\uFEFF");
    for (String[] row : rows) {
      for (int i = 0; i < row.length; i++) {
        if (i > 0) csv.append(',');
        csv.append(cell(row[i]));
      }
      csv.append("\r\n");
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String date(LocalDateTime value) {
    return value == null ? "" : value.format(DATE);
  }

  private static String cell(String value) {
    String safe = value == null ? "" : value;
    String leading = safe.stripLeading();
    if (!leading.isEmpty() && "=+-@\t\r\n".indexOf(leading.charAt(0)) >= 0) safe = "'" + safe;
    return "\"" + safe.replace("\"", "\"\"") + "\"";
  }
}
