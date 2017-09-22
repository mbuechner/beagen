/* 
 * Copyright 2017 Michael Büchner.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ddb.labs.beagen.servlets;

import de.ddb.labs.beagen.beacon.BeaconFile;
import de.ddb.labs.beagen.beacon.BeaconFile.SECTOR;
import de.ddb.labs.beagen.helper.DatabaseHelper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Michael Büchner
 */
public class ListNewestBeaconServlet extends HttpServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.reset();

        final String rs = request.getPathInfo();
        final Pattern p = Pattern.compile("\\/beacon\\-ddb\\-(persons|organizations)(\\-[a-z]*)?\\.txt");
        final Matcher m = p.matcher(rs);
        SECTOR sector = null;

        if (m.matches()) {
            String[] rss = rs.split("\\-");
            if (rss.length == 3) {
                sector = SECTOR.ALL;
            } else if (rss.length == 4) {
                for (SECTOR stmp : SECTOR.values()) {
                    if (stmp != SECTOR.ALL && rss[3].startsWith(stmp.getFileName())) {
                        sector = stmp;
                        break;
                    }
                }
            }
        }

        if (sector != null) {
            final BeaconFile bf = DatabaseHelper.getLastBeaconFile(sector);
            response.setContentType("text/plain");
            response.setContentLength(bf.getContent().length);
            response.setHeader("Content-Encoding", "gzip");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + bf.getFilename(true) + "\"");

            try (OutputStream output = response.getOutputStream()) {
                output.write(bf.getContent());
            }
            
        } else {

            final List<BeaconFile> filesList = DatabaseHelper.getLastBeaconFile();

            response.reset();
            response.setContentType("text/html");

            final PrintWriter pr = response.getWriter();

            pr.print("<!doctype html>");
            pr.print("<html lang=\"en\">");
            pr.print("<head>");
            pr.print("<meta charset=\"utf-8\">");
            pr.print("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            pr.print("<title>Beagen - Index</title>");
            pr.print("</head>");
            pr.print("<body>");

            pr.print("<h1>Index of /beagen</h1>");
            pr.print("<hr>");

            pr.print("<table style=\"border-collapse: separate; border-spacing: 10px;\">");
            pr.print("<tr>");
            pr.print("<th>File</th>");
            pr.print("<th>Last Modified</th>");
            pr.print("<th>Entity Count</th>");
            pr.print("<th>Name</th>");
            pr.print("</tr>");

            for (BeaconFile bf : filesList) {
                pr.print("<tr>");
                pr.print("<td>");
                pr.print("<a href=\"/item?id=" + bf.getId() + "\">");
                pr.print(bf.getFilename(false));
                pr.print("</a>");
                pr.print("</td>");
                pr.print("<td>");
                pr.print(bf.getCreated());
                pr.print("</td>");
                pr.print("<td>");
                pr.print(bf.getCount());
                pr.print("</td>");
                pr.print("<td>");
                pr.print(bf.getType().getHumanName());
                pr.print("</td>");
                pr.print("</tr>");
            }

            pr.print("</table>");
            pr.print("<hr>");
            pr.print("</body>");
            pr.print("</html>");
        }
    }
}
