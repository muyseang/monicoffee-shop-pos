# Use PyMySQL as the MySQL driver (pure Python, no compiler needed).
import pymysql

pymysql.version_info = (2, 2, 1, "final", 0)
pymysql.install_as_MySQLdb()
