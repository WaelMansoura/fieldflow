-- Données de démonstration : les trois techniciens.
INSERT INTO technicien (nom) VALUES ('Jean'), ('Jacques'), ('Pierre') ON CONFLICT DO NOTHING;
