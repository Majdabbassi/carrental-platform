package com.back.car_rent.document;

import com.back.car_rent.config.ApiException;
import com.back.car_rent.model.Car;
import com.back.car_rent.model.Client;
import com.back.car_rent.model.Contract;
import com.back.car_rent.repository.CarRepository;
import com.back.car_rent.repository.ClientRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Renders a contract as a printable rental agreement (with signature boxes) or as an invoice. */
@Service
public class ContractDocumentService {

    public enum Kind { CONTRACT, INVOICE }

    private static final Color INK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color ACCENT = new Color(79, 70, 229);
    private static final Color LINE = new Color(226, 232, 240);

    private static final Font TITLE = new Font(Font.HELVETICA, 22, Font.BOLD, INK);
    private static final Font AGENCY = new Font(Font.HELVETICA, 12, Font.BOLD, ACCENT);
    private static final Font H = new Font(Font.HELVETICA, 10, Font.BOLD, MUTED);
    private static final Font BODY = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);
    private static final Font BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, INK);
    private static final Font SMALL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);

    private final ClientRepository clients;
    private final CarRepository cars;

    @Value("${app.agency.name:Demo Car Rental}")
    private String agency;
    @Value("${app.agency.currency:TND}")
    private String currency;

    public ContractDocumentService(ClientRepository clients, CarRepository cars) {
        this.clients = clients;
        this.cars = cars;
    }

    public static Kind kind(String value) {
        if (value == null || value.equalsIgnoreCase("contract")) {
            return Kind.CONTRACT;
        }
        if (value.equalsIgnoreCase("invoice")) {
            return Kind.INVOICE;
        }
        throw ApiException.badRequest("type must be contract or invoice");
    }

    public byte[] render(Contract c, Kind kind) {
        Client client = c.getClientName() == null ? null
                : clients.findAll().stream().filter(x -> c.getClientName().equalsIgnoreCase(x.getFullName())).findFirst().orElse(null);
        Car car = c.getLicensePlate() == null ? null : cars.findFirstByLicensePlate(c.getLicensePlate()).orElse(null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            header(doc, c, kind);
            parties(doc, c, client, car);
            terms(doc, c);
            if (kind == Kind.INVOICE) {
                invoice(doc, c);
            } else {
                agreedPrice(doc, c);
                conditions(doc);
                signatures(doc);
            }
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render the PDF", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ sections

    private void header(Document doc, Contract c, Kind kind) throws DocumentException {
        PdfPTable t = table(2, 60, 40);
        PdfPCell left = cell();
        left.addElement(new Paragraph(agency, AGENCY));
        left.addElement(new Paragraph(kind == Kind.INVOICE ? "Invoice" : "Rental agreement", TITLE));
        PdfPCell right = cell();
        Paragraph ref = new Paragraph();
        ref.setAlignment(Element.ALIGN_RIGHT);
        ref.add(new Phrase((kind == Kind.INVOICE ? "Invoice " : "Contract ")
                + (kind == Kind.INVOICE ? "INV-" : "") + nz(c.getContractId()) + "\n", BOLD));
        ref.add(new Phrase("Issued " + LocalDate.now() + "\n", SMALL));
        ref.add(new Phrase("Status: " + nz(c.getStatus()), SMALL));
        right.addElement(ref);
        t.addCell(left);
        t.addCell(right);
        doc.add(t);
        rule(doc);
    }

    private void parties(Document doc, Contract c, Client client, Car car) throws DocumentException {
        PdfPTable t = table(2, 50, 50);
        PdfPCell a = cell();
        a.addElement(new Paragraph("CLIENT", H));
        a.addElement(new Paragraph(nz(c.getClientName()), BOLD));
        a.addElement(new Paragraph(nz(c.getClientPhone() != null ? c.getClientPhone() : client != null ? client.getPhone() : null), BODY));
        if (client != null) {
            line(a, client.getEmail());
            line(a, client.getAddress());
            if (client.getDrivingLicenseNumber() != null) {
                a.addElement(new Paragraph("Driving licence " + client.getDrivingLicenseNumber()
                        + (client.getLicenseExpiryDate() != null ? " (valid to " + client.getLicenseExpiryDate() + ")" : ""), BODY));
            }
            if (client.getNationalId() != null) {
                a.addElement(new Paragraph("National ID " + client.getNationalId(), BODY));
            }
        }
        PdfPCell b = cell();
        b.addElement(new Paragraph("VEHICLE", H));
        b.addElement(new Paragraph(nz(c.getCarMake()) + " " + nz(c.getCarModel()), BOLD));
        b.addElement(new Paragraph("Plate " + nz(c.getLicensePlate()), BODY));
        if (car != null) {
            if (car.getYear() != null || car.getColor() != null) {
                b.addElement(new Paragraph((car.getYear() != null ? car.getYear() + " " : "") + nz(car.getCategory()), BODY));
            }
            if (car.getMileage() != null) {
                b.addElement(new Paragraph("Mileage at pick-up: " + car.getMileage() + " km", BODY));
            }
            if (car.getFuelType() != null) {
                b.addElement(new Paragraph(car.getFuelType() + ", " + nz(car.getTransmission()), BODY));
            }
        }
        t.addCell(a);
        t.addCell(b);
        doc.add(t);
        rule(doc);
    }

    private void terms(Document doc, Contract c) throws DocumentException {
        PdfPTable t = table(4, 25, 25, 25, 25);
        t.addCell(labelled("PICK-UP", c.getStartDate()));
        t.addCell(labelled("RETURN", c.getEndDate()));
        t.addCell(labelled("DAYS", String.valueOf(days(c))));
        t.addCell(labelled("RENTAL TYPE", c.getRentalType()));
        doc.add(t);
        doc.add(spacer(8));
    }

    private void invoice(Document doc, Contract c) throws DocumentException {
        long days = days(c);
        double rate = c.getDailyRate() == null ? 0 : c.getDailyRate();
        double total = c.getTotalValue() == null ? rate * days : c.getTotalValue();
        double listed = rate * days;

        PdfPTable t = table(4, 52, 12, 18, 18);
        for (String head : new String[]{"DESCRIPTION", "QTY", "UNIT", "AMOUNT"}) {
            PdfPCell h = new PdfPCell(new Phrase(head, H));
            h.setBorder(Rectangle.BOTTOM);
            h.setBorderColor(LINE);
            h.setPadding(6);
            if (!head.equals("DESCRIPTION")) {
                h.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(h);
        }
        row(t, "Rental of " + nz(c.getCarMake()) + " " + nz(c.getCarModel()) + ", " + c.getStartDate() + " to " + c.getEndDate(),
                days + (days == 1 ? " day" : " days"), money(rate), money(listed));
        double adjustment = total - listed;
        if (Math.abs(adjustment) >= 0.005) {
            row(t, adjustment < 0 ? "Discount (long stay, offers)" : "Season supplement", "", "", money(adjustment));
        }
        doc.add(t);
        doc.add(spacer(10));

        PdfPTable sums = table(2, 70, 30);
        sums.addCell(plain("Total", BOLD, Element.ALIGN_RIGHT));
        sums.addCell(plain(money(total), BOLD, Element.ALIGN_RIGHT));
        String payment = nz(c.getPaymentStatus());
        boolean paid = "Paid".equalsIgnoreCase(payment);
        sums.addCell(plain("Payment: " + payment + (c.getPaymentMethod() != null ? " (" + c.getPaymentMethod() + ")" : ""),
                BODY, Element.ALIGN_RIGHT));
        sums.addCell(plain(paid ? "PAID" : "", AGENCY, Element.ALIGN_RIGHT));
        sums.addCell(plain(paid ? "Amount due" : "Amount due (before any partial payment)", BOLD, Element.ALIGN_RIGHT));
        sums.addCell(plain(money(paid ? 0 : total), BOLD, Element.ALIGN_RIGHT));
        if (c.getDeposit() != null && c.getDeposit() > 0) {
            sums.addCell(plain("Security deposit (refundable, not included above)", SMALL, Element.ALIGN_RIGHT));
            sums.addCell(plain(money(c.getDeposit()), SMALL, Element.ALIGN_RIGHT));
        }
        doc.add(sums);
        doc.add(spacer(18));
        doc.add(new Paragraph("Thank you for renting with " + agency + ".", SMALL));
    }

    private void agreedPrice(Document doc, Contract c) throws DocumentException {
        long days = days(c);
        double rate = c.getDailyRate() == null ? 0 : c.getDailyRate();
        double total = c.getTotalValue() == null ? rate * days : c.getTotalValue();
        PdfPTable t = table(4, 25, 25, 25, 25);
        t.addCell(labelled("DAILY RATE", money(rate)));
        t.addCell(labelled("TOTAL RENTAL", money(total)));
        t.addCell(labelled("SECURITY DEPOSIT", c.getDeposit() == null ? "-" : money(c.getDeposit())));
        t.addCell(labelled("PAYMENT", nz(c.getPaymentStatus()) + (c.getPaymentMethod() != null ? ", " + c.getPaymentMethod() : "")));
        doc.add(t);
        rule(doc);
    }

    private void conditions(Document doc) throws DocumentException {
        doc.add(new Paragraph("CONDITIONS OF RENTAL", H));
        doc.add(spacer(4));
        String[] terms = {
                "The client drives the vehicle personally, holds a valid driving licence and is the only authorised driver unless stated here.",
                "The vehicle is handed over with the fuel level and mileage recorded at pick-up and must be returned with the same fuel level.",
                "Returning the vehicle after the agreed date is charged per extra day at the daily rate.",
                "The client is responsible for fines, tolls and damage not covered by the insurance, and for the loss of keys or documents.",
                "The security deposit is refunded when the vehicle is returned in the condition it was handed over.",
                "Smoking, driving outside the agreed territory, subletting and racing are forbidden."};
        for (int i = 0; i < terms.length; i++) {
            Paragraph p = new Paragraph((i + 1) + ".  " + terms[i], BODY);
            p.setSpacingAfter(3);
            doc.add(p);
        }
        doc.add(spacer(14));
    }

    private void signatures(Document doc) throws DocumentException {
        PdfPTable t = table(2, 50, 50);
        for (String who : new String[]{"The client", "For " + agency}) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.BOX);
            c.setBorderColor(LINE);
            c.setPadding(8);
            c.setFixedHeight(96);
            c.addElement(new Paragraph(who, H));
            c.addElement(new Paragraph("Read and approved. Signature:", SMALL));
            t.addCell(c);
        }
        doc.add(t);
        doc.add(spacer(6));
        doc.add(new Paragraph("Date and place: ____________________________", SMALL));
    }

    // ------------------------------------------------------------------ helpers

    private static long days(Contract c) {
        try {
            return Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(c.getStartDate()), LocalDate.parse(c.getEndDate())));
        } catch (Exception e) {
            return 1;
        }
    }

    private String money(double value) {
        return String.format(Locale.ROOT, "%.2f %s", value, currency);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static void line(PdfPCell cell, String text) {
        if (text != null && !text.isBlank()) {
            cell.addElement(new Paragraph(text, BODY));
        }
    }

    private static PdfPTable table(int cols, float... widths) throws DocumentException {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setWidths(widths);
        return t;
    }

    private static PdfPCell cell() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4);
        return c;
    }

    private static PdfPCell plain(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }

    private static PdfPCell labelled(String label, String value) {
        PdfPCell c = cell();
        c.addElement(new Paragraph(label, H));
        c.addElement(new Paragraph(value == null ? "-" : value, BOLD));
        return c;
    }

    private static void row(PdfPTable t, String description, String qty, String unit, String amount) {
        t.addCell(plain(description, BODY, Element.ALIGN_LEFT));
        t.addCell(plain(qty, BODY, Element.ALIGN_RIGHT));
        t.addCell(plain(unit, BODY, Element.ALIGN_RIGHT));
        t.addCell(plain(amount, BODY, Element.ALIGN_RIGHT));
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private static void rule(Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(" ", SMALL));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setFixedHeight(6);
        t.addCell(c);
        doc.add(t);
        doc.add(spacer(8));
    }
}
