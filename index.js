require('dotenv').config();
const express = require('express');

const app = express();

// Middleware to parse incoming JSON requests
app.use(express.json());

// A simple health-check endpoint to verify the server is running
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'UP', timestamp: new Date().toISOString() });
});

const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log(`SSO Server is running on http://localhost:${PORT}`);
});