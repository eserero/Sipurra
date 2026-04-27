ALTER TABLE ImageReaderSettings ADD COLUMN ocr_engine TEXT NOT NULL DEFAULT 'ML_KIT';
ALTER TABLE ImageReaderSettings ADD COLUMN ocr_rapid_ocr_model TEXT NOT NULL DEFAULT 'ENGLISH_CHINESE';
ALTER TABLE ImageReaderSettings ADD COLUMN rapid_ocr_models_url TEXT NOT NULL DEFAULT 'https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/RapidOcrModels.zip';
