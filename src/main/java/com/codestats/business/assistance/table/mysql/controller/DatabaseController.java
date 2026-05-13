package com.codestats.business.assistance.table.mysql.controller;

import com.framework.jdbc.metedata.datamodel.DataModel;
import com.framework.jdbc.metedata.service.DatabaseMetadataService;

import com.framework.spring.annotation.Autowired;
import com.framework.spring.annotation.Controller;
import com.framework.spring.annotation.RequestMapping;
import com.framework.spring.annotation.ResponseBody;
import com.framework.util.DataModelConverter;

@Controller
@RequestMapping("/metadata")
public class DatabaseController {

    @Autowired
    private DatabaseMetadataService metadataService;

    @RequestMapping("/databases")
    @ResponseBody
    public String getDatabases() {
        try {
            DataModel dm = metadataService.getDatabasesAsDataModel();
            if (dm.isEmpty()) {
                return "<h3>No databases found.</h3>";
            }
            StringBuilder html = new StringBuilder();
            html.append("<html><body>");
            html.append("<h2>Database List</h2>");
            html.append(DataModelConverter.toHtmlTable(dm));
            html.append("</body></html>");
            return html.toString();
        } catch (Exception e) {
            return "<h3>Error fetching databases: " + e.getMessage() + "</h3>";
        }
    }

    @RequestMapping("/tables")
    @ResponseBody
    public String getTables() {
        try {
            DataModel dm = metadataService.getTablesAsDataModel();
            if (dm.isEmpty()) {
                return "<h3>No tables found in default database.</h3>";
            }
            StringBuilder html = new StringBuilder();
            html.append("<html><body>");
            String dbName = metadataService.getDefaultDatabase().getName();
            html.append("<h2>Tables in ").append(dbName).append("</h2>");
            html.append(DataModelConverter.toHtmlTable(dm));
            html.append("</body></html>");
            return html.toString();
        } catch (Exception e) {
            return "<h3>Error fetching tables: " + e.getMessage() + "</h3>";
        }
    }

    @RequestMapping("/columns")
    @ResponseBody
    public String getColumns() {
        String tableName = "user";
        try {
            DataModel dm = metadataService.getColumnsAsDataModel(tableName);
            if (dm.isEmpty()) {
                return "<h3>No columns found for table: " + tableName + "</h3>";
            }
            StringBuilder html = new StringBuilder();
            html.append("<html><body>");
            String dbName = metadataService.getDefaultDatabase().getName();
            html.append("<h2>Columns of ").append(dbName).append(".").append(tableName).append("</h2>");
            html.append(DataModelConverter.toHtmlTable(dm));
            html.append("</body></html>");
            return html.toString();
        } catch (Exception e) {
            return "<h3>Error fetching columns: " + e.getMessage() + "</h3>";
        }
    }
}