import psycopg2


def restartBd():
        # Global constant
        PSQL_HOST = "localhost"
        PSQL_PORT = "5432"
        PSQL_USER = "postgres"
        PSQL_PASS = "postgres"
        PSQL_DB = "postgres"

        # Connection
        connection_address = """
        host=%s port=%s user=%s password=%s dbname=%s
        """ % (PSQL_HOST, PSQL_PORT, PSQL_USER, PSQL_PASS, PSQL_DB)
        connection = psycopg2.connect(connection_address)

        cursor = connection.cursor()

        # Query
        SQL = "DELETE FROM ibex;"
        cursor.execute(SQL)
        rows_deleted = cursor.rowcount
        # Get Values
        connection.commit()
        cursor.close()
        connection.close()

        print('Reinicio BD POPSTGRES.')