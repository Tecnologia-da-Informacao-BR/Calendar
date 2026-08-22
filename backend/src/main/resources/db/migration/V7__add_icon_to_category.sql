-- V7 - add the icon returned by the categories API

ALTER TABLE category
    ADD COLUMN icon varchar(255);
