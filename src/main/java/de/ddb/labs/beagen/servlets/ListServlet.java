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

public class ListServlet extends HttpServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);         
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
        final EntityManager em = EntityManagerUtil.getInstance().getEntityManager();
        final Query q = em.createNativeQuery("SELECT f.created FROM FILES f GROUP BY f.created ORDER BY f.created DESC");
        final List<Date> lastDateList = q.getResultList();
        
        response.reset();      
        response.setContentType("text/html");
        
               final PrintWriter pr = response.getWriter();

        pr.print("<!doctype html>");
        pr.print("<html lang=\"en\">");
        pr.print("<head>");
        pr.print("<meta charset=\"utf-8\">");
        pr.print("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        pr.print("<title>Beagen - Dates</title>");
        pr.print("</head>");
        pr.print("<body>");

        pr.print("<h1>List of availible dates</h1>");
        pr.print("<hr>");
        pr.print("<ul>");
        
        for(Date date : lastDateList) {
            pr.print("<li><a href=\"/xxx?date="+date.getTime()+"\">");
            pr.print(date);
            pr.print("</a></li>");
        }
        
        pr.print("</ul>");
        pr.print("<hr>");
        pr.print("</body>");
        pr.print("</html>");
    }
}
