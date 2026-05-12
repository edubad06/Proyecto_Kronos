/*
 * CONFIGURACIÓN E INICIALIZACIÓN FIREBASE
 */
const firebaseConfig = {
  apiKey: "AIzaSyCddmLjM1PwmP0ZC3Pd9P6P9h8iuXUPHWg",
  authDomain: "kronos-e3625.firebaseapp.com",
  projectId: "kronos-e3625",
  storageBucket: "kronos-e3625.firebasestorage.app",
  messagingSenderId: "573047841332",
  appId: "1:573047841332:web:a2d4068cd4d31b6516e061",
  measurementId: "G-WJGGT2H6W9"
};


//Inicialización Firebase
firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();
const auth = firebase.auth();

const AWS_API_KEY = 'jFOzFueL9B8RB9MIzrSJ08CMFq73tG0f8bbeHJHf';