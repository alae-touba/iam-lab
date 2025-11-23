-- Script to set up the session_auth_db database
-- Run this script in your MySQL client to create the database and user

-- Create the database
CREATE DATABASE IF NOT EXISTS session_auth_db;

-- Create a user for the application (optional, you can use root)
-- CREATE USER 'session_user'@'localhost' IDENTIFIED BY 'session_password';

-- Grant privileges to the user (optional)
-- GRANT ALL PRIVILEGES ON session_auth_db.* TO 'session_user'@'localhost';

-- Use the database
USE session_auth_db;

-- The tables will be created automatically by Flyway when the application starts