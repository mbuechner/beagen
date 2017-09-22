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
import de.ddb.labs.beagen.helper.EntityManagerUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ListOlderBeaconServlet extends HttpServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();

        final Query q1 = em.createNativeQuery("SELECT f.created FROM FILES f GROUP BY f.created ORDER BY f.created DESC");
        q1.setMaxResults(1);
        final List<Date> lastDateList = q1.getResultList();

        Date lastDate;
        // select last date
        if (lastDateList != null && !lastDateList.isEmpty()) {
            lastDate = (Date) lastDateList.get(0);
        } else {
            lastDate = new Date();
        }

        final Query q2 = em
                .createNativeQuery("SELECT * FROM FILES f WHERE f.created < :oldDate ORDER BY f.created DESC, f.type", BeaconFile.class)
                .setParameter("oldDate", lastDate);
        final List<BeaconFile> filesList = q2.getResultList();

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
            pr.print(bf.getFilename(true));
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
