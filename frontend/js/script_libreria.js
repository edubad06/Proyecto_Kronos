//DOM
const ficha_jaciment = document.querySelector(".ficha_jac");
const ficha_sector = document.querySelector(".ficha_sec");
const ficha_ue = document.querySelector(".ficha_ue");

ficha_jaciment.addEventListener("click", function(){
    console.log("click en yacimiento")
    window.location.assign("detail.html?tab=yacimiento");
});

ficha_sector.addEventListener("click", function(){
    window.location.assign(`detail.html?tab=sector&id=SEC-A`);
});

ficha_ue.addEventListener("click", function(){
    window.location.assign("detail.html?tab=ue");
});