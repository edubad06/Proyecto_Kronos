//DOM
const btn_ue = document.querySelector("#pestana-ue");
const btn_jaciment = document.querySelector("#pestana-yacimiento");
const btn_sector = document.querySelector("#pestana-sector");
const form_ue = document.querySelector("#formulario-ue");
const form_jaciment = document.querySelector("#formulario-yacimiento");
const form_sector = document.querySelector("#formulario-sector");


//FUNCIONES
// Canvia de pestanya activa
const cambiarPestana = function() {
    document.querySelectorAll('.boton-selector-pestana').forEach(boton => boton.classList.remove('activa'));
    document.querySelectorAll('.bloque-pestana').forEach(formulario => formulario.classList.remove('activa'));
};

// Comprova la URL carregada per obrir la pestanya correcta
window.onload = function () {
    const parametrosUrl = new URLSearchParams(window.location.search);
    const idA_ubrir = parametrosUrl.get('tab');
    if (idA_ubrir==='ue') {
        btn_ue.classList.add('activa');
        form_ue.classList.add('activa');
    }
};

btn_ue.addEventListener("click", function(){
    cambiarPestana();
    btn_ue.classList.add('activa');
    form_ue.classList.add('activa');
});

btn_jaciment.addEventListener("click", function(){
    cambiarPestana();
    btn_jaciment.classList.add('activa');
    form_jaciment.classList.add('activa');
});

btn_sector.addEventListener("click", function(){
    cambiarPestana();
    btn_sector.classList.add('activa');
    form_sector.classList.add('activa');
});