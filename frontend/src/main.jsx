import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from "react-router-dom";
import './index.css'
import './setupApi' // 상대 경로 API 호출에 VITE_API_URL 적용 (다른 import 보다 먼저 실행되어야 한다)
import App from './App.jsx'


createRoot(document.getElementById('root')).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>
)

// import React from 'react';
// import ReactDOM from 'react-dom/client';
// import { BrowserRouter } from 'react-router-dom';
// import App from './App';
// import './index.css';

// ReactDOM.createRoot(document.getElementById('root')).render(
//   <React.StrictMode>
//     <BrowserRouter> {/* 여기서 감싸주는 게 가장 확실합니다 */}
//       <App />
//     </BrowserRouter>
//   </React.StrictMode>
// );

