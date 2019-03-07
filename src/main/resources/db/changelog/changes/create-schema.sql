--liquibase formatted sql

--changeset author:david z
CREATE TABLE if not exists app_user (id varchar(100), agency_id bigint, geo_id bigint
, permission_role varchar(100), app_name varchar(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE if not exists import_status (last_import date) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE if not exists user_data_store (id varchar(100) NOT NULL, first_name varchar(100), last_name varchar(100)
, login_email varchar(100), last_login datetime, agency_id bigint NOT NULL, agency_name varchar(255) NOT NULL
, geo_id bigint NOT NULL, geo_name varchar(255) NOT NULL, data_center varchar(16) NOT NULL
, permission_role varchar(100) NOT NULL, PRIMARY KEY (id, agency_id, geo_id, data_center, permission_role)) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE if not exists users (id varchar(100), first_name varchar(100), last_name varchar(100)
, login_email varchar(100), last_login datetime) ENGINE=InnoDB DEFAULT CHARSET=utf8;

