
# --- !Ups

CREATE TABLE users(
    name VARCHAR(255) NOT NULL PRIMARY KEY, 
    password VARCHAR(255) NOT NULL
);

INSERT INTO users (name, password) VALUES
    ('simon', '123'),
    ('rofelator', 'rofl')

# --- !Downs

DROP TABLE users;
