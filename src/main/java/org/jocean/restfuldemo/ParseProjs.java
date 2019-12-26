package org.jocean.restfuldemo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

public class ParseProjs {

    private static String[] parseMonthsfYear(final String year, final String html) {
        final String title = "<th>"+ year +"</th>\n";
        final int begin = html.indexOf(title);
        if (begin > -1) {
            final int end = html.indexOf("</tr>", begin + title.length());
            if (end > -1) {
                final String monthString = html.substring(begin + title.length(), end);
                final String[] months = monthString.split("\n");
                for (int i = 0; i < months.length; i++) {
                    months[i] = months[i].replaceAll("<td>", "").replaceAll("</td>", "");
                }
                return months;
            }
        }
        return new String[]{"0","0","0","0","0","0","0","0","0","0","0","0"};
    }

    static private final String statsSuffix = "/activity/year_month.html";
    public static void main(final String[] args) throws Exception {
        final File xlsIn = new File(args[0]);
        final File xlsOut = new File(args[1]);
        final String statsBase = args[2];
        // 读取xls文件流
        final InputStream is = new FileInputStream(xlsIn.getAbsolutePath());
        final OutputStream os = new FileOutputStream(xlsOut.getAbsolutePath());

        final Workbook rwb = Workbook.getWorkbook(is);

        final WorkbookSettings settings = new WorkbookSettings();

        settings.setWriteAccess(null);
        final WritableWorkbook wwb = Workbook.createWorkbook(os, rwb, settings);

        // 获取当前excel中共有几个表
        final Sheet from = rwb.getSheet(0);

        final WritableSheet to = wwb.getSheet(0);
        {
            int base = 2;
            to.addCell(new jxl.write.Label(base +1, 0, "201801"));
            to.addCell(new jxl.write.Label(base +2, 0, "201802"));
            to.addCell(new jxl.write.Label(base +3, 0, "201803"));
            to.addCell(new jxl.write.Label(base +4, 0, "201804"));
            to.addCell(new jxl.write.Label(base +5, 0, "201805"));
            to.addCell(new jxl.write.Label(base +6, 0, "201806"));
            to.addCell(new jxl.write.Label(base +7, 0, "201807"));
            to.addCell(new jxl.write.Label(base +8, 0, "201808"));
            to.addCell(new jxl.write.Label(base +9, 0, "201809"));
            to.addCell(new jxl.write.Label(base +10, 0, "201810"));
            to.addCell(new jxl.write.Label(base +11, 0, "201811"));
            to.addCell(new jxl.write.Label(base +12, 0, "201812"));
            base = 14;
            to.addCell(new jxl.write.Label(base +1, 0, "201901"));
            to.addCell(new jxl.write.Label(base +2, 0, "201902"));
            to.addCell(new jxl.write.Label(base +3, 0, "201903"));
            to.addCell(new jxl.write.Label(base +4, 0, "201904"));
            to.addCell(new jxl.write.Label(base +5, 0, "201905"));
            to.addCell(new jxl.write.Label(base +6, 0, "201906"));
            to.addCell(new jxl.write.Label(base +7, 0, "201907"));
            to.addCell(new jxl.write.Label(base +8, 0, "201908"));
            to.addCell(new jxl.write.Label(base +9, 0, "201909"));
            to.addCell(new jxl.write.Label(base +10, 0, "201910"));
            to.addCell(new jxl.write.Label(base +11, 0, "201911"));
            to.addCell(new jxl.write.Label(base +12, 0, "201912"));
        }
        for (int row = 1; row < from.getRows(); row++) {
            final String proj = from.getCell(2, row).getContents().trim();
            if (!proj.isEmpty()) {
                System.out.println(row + ":" + proj);
                final String statsFilename = statsBase + proj + statsSuffix;
                final String html = Files.asCharSource(new File(statsFilename), Charsets.UTF_8).read();

                final String[] m2018 = parseMonthsfYear("2018", html);
                System.out.println("2018:" + Arrays.toString(m2018));
                int col = 3;
                for (final String m : m2018) {
                    to.addCell(new jxl.write.Number(col, row, Integer.parseInt(m)));
                    col++;
                }

                final String[] m2019 = parseMonthsfYear("2019", html);
                System.out.println("2019:" + Arrays.toString(m2019));
                for (final String m : m2019) {
                    to.addCell(new jxl.write.Number(col, row, Integer.parseInt(m)));
                    col++;
                }
            }
        }
        wwb.write();
        wwb.close();
    }

}
