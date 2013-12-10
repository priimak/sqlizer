/************************************************************************
 *
 * NetDBPostFilter.java -
 *
 * $Id: NetDBPostFilter.java,v 1.4 2008/07/30 17:49:29 priimak Exp $
 *
 *-----------------------------------------------------------------------
 * Copyright (c) 2008, Board of Trustees, Leland Stanford Jr. University
 ************************************************************************/
package stanford.netdb.utils;

import java.util.regex.*;

/**
 * Stanford NetDB Specific PostFilter.
 */
public class NetDBPostFilter extends PostFilter {

    public static Pattern column1_p = Pattern.compile("^\\s*lower\\((.+)\\)\\s*$");
    public static Pattern column2_p = Pattern.compile("^\\s*(.*)_lc\\s*$");

    /**
     * If column name is lower(column_name) or column_name_lc then field will be
     * transformed to lower case if it is not REGEXP_LIKE expression.
     */
    public String field_filter(String expr, String table, String column, String field)
        throws Exception
    {
        if( !expr.equals("REGEXP_LIKE") ) {
            if( column1_p.matcher(column).matches() ||
                column2_p.matcher(column).matches() )
                return field.toLowerCase();
            else
                return field;
        } else
            return field;
    }

    /**
     * If it is REGEXP_LIKE expression and column name is lower(column_name) or
     * column_name_lc then column name will be transformed into column_name.
     */
    public String column_filter(String expr, String table, String column, String field)
        throws Exception
    {
        if( expr.equals("REGEXP_LIKE") ) {
            Matcher column1_m = column1_p.matcher(column);
            Matcher column2_m = column2_p.matcher(column);
            if( column1_m.matches() )
                return column1_m.group(1).trim();
            else if( column2_m.matches() )
                return column2_m.group(1).trim();
            else
                return column;
        } else
            return column;
    }
}
