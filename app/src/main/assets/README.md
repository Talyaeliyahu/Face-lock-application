# הוראות הוספת מודל FaceNet

## איפה לשים את הקובץ:

הקובץ צריך להיות בתיקייה:
```
app/src/main/assets/mobilefacenet.tflite
```

## איך להוריד:

1. **MobileFaceNet (מומלץ - מהיר יותר)**:
   - הורד את `mobilefacenet.tflite` (~4MB)
   - שים אותו בתיקייה `app/src/main/assets/`
   - הקובץ כבר קיים: ✅ `mobilefacenet.tflite`

2. **FaceNet (מדויק יותר אבל איטי)**:
   - אם תרצה להשתמש ב-FaceNet במקום:
   - הורד את `facenet.tflite` (~23MB)
   - שים אותו בתיקייה `app/src/main/assets/`
   - שנה בקוד את `mobilefacenet.tflite` ל-`facenet.tflite`
   - שנה את `embeddingSize` ל-512
   - שנה את `inputSize` ל-160

## מבנה התיקיות:

```
app/
  src/
    main/
      assets/          ← כאן!
        mobilefacenet.tflite  ← הקובץ שלך
        README.md      ← הקובץ הזה
```

## הערות:

- הקובץ `mobilefacenet.tflite` כבר קיים בתיקייה
- הקוד מוגדר להשתמש ב-MobileFaceNet (128 dimensions, input 112x112)
- אם תרצה לשנות ל-FaceNet, עקוב אחר ההוראות למעלה
