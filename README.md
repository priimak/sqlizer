Name
====
    SQLizer - Java utility library intended for generating sql from the search input.

Synopsis
========

    import stanford.netdb.utils.*;

    String sql_1 = Feild2SQL.parseField(type,table, column, input_str, field_display_name, post_filter);

    String sql_2 = Feild2SQL.parseStringField(table, column, input_str, field_display_name);

    String sql_3 = Feild2SQL.parseIntField(table, column, line, field_display_name);

Description

    SQLizer is an utility library intented to be used in search applications. Given an input string it builds SQL select statement. It supports two types of inputs, numeric and string. The catch is that this library allows usage of operators in the input string. Supported operators are 'and', 'or' and 'not'. It also supports wildcards '*', '%', '?' and '_' in string searches. In case of numeric searches no wildcards are supported. To accommodate searches by strings, which have wildcards in them, backslashes '\' can be used to escape appropriate characters. Obviously to search for '\' it needs to be escaped as well. Strings with spaces needs to be enclosed in double quotes. The other possibility is to do a regex search, which is accomplished by enclosing string in forward slashes. The resulting SQL is targeted toward Oracle regex expressions. For example, following input strings will be appropriately evaluated.

     a or b
       -> ( ( select id from Record where name = 'a' ) UNION ( select id from Record where name = 'b' ) )

     not a and not b
       -> ( ( select id from Record MINUS ( select id from Record where name = 'a' ) ) INTERSECT ( select id from Record MINUS ( select id from Record where name = 'b' ) ) )

     "a b c" or a\\*
       -> ( ( select id from Record where name = 'a b c' ) UNION ( select id from Record where name LIKE 'a\\%' ESCAPE '\' ) )

     /^.*[[:digit:]]$/
       -> ( select id from Record where REGEXP_LIKE(name, '^.*[[:digit:]]$', 'i') )

    We use unions and intersects in order to accomodate searches for objects with multi-value attributes.

Requirements

    Java 1.5 or higher.
    ANTLR v2 only.
    It was designed and tested with Java 1.5 and Oracle 10 database.

To do

    Migrate to ANTLR v3

P.S.

    If you have any questions please contact me at priimak@gmail.com
