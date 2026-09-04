import React from 'react';
import { Link } from 'react-router-dom';

export const Footer: React.FC = () => {
  return (
    <footer>
      <div className="container">
        <div className="footer-section">
          <h3>ClaimAssist</h3>
          <p style={{ fontSize: '14px', color: '#CCCCCC', maxWidth: '250px' }}>
            Modern insurance claims management powered by AI.
          </p>
        </div>

        <div className="footer-section">
          <h3>Products</h3>
          <ul>
            <li>
              <Link to="/products">Health Insurance</Link>
            </li>
            <li>
              <Link to="/products">Vehicle Insurance</Link>
            </li>
            <li>
              <Link to="/products">Travel Insurance</Link>
            </li>
          </ul>
        </div>

        <div className="footer-section">
          <h3>Support</h3>
          <ul>
            <li>
              <Link to="/contact">Contact Us</Link>
            </li>
            <li>
              <Link to="/about">About Us</Link>
            </li>
            <li>
              <a href="#faq">FAQ</a>
            </li>
          </ul>
        </div>

        <div className="footer-section">
          <h3>Legal</h3>
          <ul>
            <li>
              <a href="#privacy">Privacy Policy</a>
            </li>
            <li>
              <a href="#terms">Terms of Service</a>
            </li>
            <li>
              <a href="#security">Security</a>
            </li>
          </ul>
        </div>
      </div>

      <div className="footer-bottom">
        <p>© {new Date().getFullYear()} ClaimAssist. All rights reserved.</p>
      </div>
    </footer>
  );
};
