// MongoDB Initialization Script for AyuSure
// This script creates the database, collections, and initial data

// Switch to the ayusure database
db = db.getSiblingDB('ayusure');

// Create collections with validation schemas
db.createCollection('users', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['username', 'email', 'password_hash', 'role', 'organization_id'],
            properties: {
                username: {
                    bsonType: 'string',
                    description: 'Username must be a string and is required'
                },
                email: {
                    bsonType: 'string',
                    pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$',
                    description: 'Email must be a valid email address'
                },
                password_hash: {
                    bsonType: 'string',
                    description: 'Password hash must be a string and is required'
                },
                role: {
                    bsonType: 'string',
                    enum: ['admin', 'manager', 'operator', 'viewer'],
                    description: 'Role must be one of admin, manager, operator, or viewer'
                },
                organization_id: {
                    bsonType: 'objectId',
                    description: 'Organization ID must be an ObjectId and is required'
                },
                is_active: {
                    bsonType: 'bool',
                    description: 'Active status must be a boolean'
                }
            }
        }
    }
});

db.createCollection('organizations', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['name', 'created_at'],
            properties: {
                name: {
                    bsonType: 'string',
                    description: 'Organization name must be a string and is required'
                },
                settings: {
                    bsonType: 'object',
                    description: 'Settings must be an object'
                }
            }
        }
    }
});

db.createCollection('devices', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['device_id', 'device_model', 'firmware_version', 'organization_id', 'owner_user_id'],
            properties: {
                device_id: {
                    bsonType: 'string',
                    description: 'Device ID must be a string and is required'
                },
                device_model: {
                    bsonType: 'string',
                    description: 'Device model must be a string and is required'
                },
                firmware_version: {
                    bsonType: 'string',
                    description: 'Firmware version must be a string and is required'
                },
                status: {
                    bsonType: 'string',
                    enum: ['active', 'inactive', 'maintenance'],
                    description: 'Status must be active, inactive, or maintenance'
                },
                organization_id: {
                    bsonType: 'objectId',
                    description: 'Organization ID must be an ObjectId and is required'
                },
                owner_user_id: {
                    bsonType: 'objectId',
                    description: 'Owner user ID must be an ObjectId and is required'
                }
            }
        }
    }
});

db.createCollection('sensor_readings', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['device_id', 'measurement_id', 'raw_readings', 'timestamp'],
            properties: {
                device_id: {
                    bsonType: 'string',
                    description: 'Device ID must be a string and is required'
                },
                measurement_id: {
                    bsonType: 'string',
                    description: 'Measurement ID must be a string and is required'
                },
                raw_readings: {
                    bsonType: 'object',
                    description: 'Raw readings must be an object and is required'
                },
                processing_status: {
                    bsonType: 'string',
                    enum: ['pending', 'processing', 'completed', 'failed'],
                    description: 'Processing status must be pending, processing, completed, or failed'
                }
            }
        }
    }
});

db.createCollection('analysis_results', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['reading_id', 'device_id', 'herb_name', 'analysis_timestamp'],
            properties: {
                reading_id: {
                    bsonType: 'objectId',
                    description: 'Reading ID must be an ObjectId and is required'
                },
                device_id: {
                    bsonType: 'string',
                    description: 'Device ID must be a string and is required'
                },
                herb_name: {
                    bsonType: 'string',
                    description: 'Herb name must be a string and is required'
                },
                analysis_timestamp: {
                    bsonType: 'date',
                    description: 'Analysis timestamp must be a date and is required'
                }
            }
        }
    }
});

db.createCollection('reports');

// Create indexes for optimal performance
print('Creating indexes...');

// Users indexes
db.users.createIndex({ 'username': 1 }, { unique: true });
db.users.createIndex({ 'email': 1 }, { unique: true });
db.users.createIndex({ 'organization_id': 1, 'role': 1 });

// Organizations indexes
db.organizations.createIndex({ 'name': 1 });
db.organizations.createIndex({ 'created_at': -1 });

// Devices indexes
db.devices.createIndex({ 'device_id': 1 }, { unique: true });
db.devices.createIndex({ 'organization_id': 1, 'status': 1 });
db.devices.createIndex({ 'owner_user_id': 1 });

// Sensor readings indexes (time series optimized)
db.sensor_readings.createIndex({ 'device_id': 1, 'timestamp': -1 });
db.sensor_readings.createIndex({ 'timestamp': -1, 'processing_status': 1 });
db.sensor_readings.createIndex({ 'measurement_id': 1 });
db.sensor_readings.createIndex({ 'sample_metadata.batch_id': 1 });

// TTL index for automatic cleanup of old sensor readings (1 year retention)
db.sensor_readings.createIndex({ 'created_at': 1 }, { expireAfterSeconds: 31536000 });

// Analysis results indexes
db.analysis_results.createIndex({ 'device_id': 1, 'analysis_timestamp': -1 });
db.analysis_results.createIndex({ 'reading_id': 1 }, { unique: true });
db.analysis_results.createIndex({ 'quality_metrics.grade': 1, 'analysis_timestamp': -1 });
db.analysis_results.createIndex({ 'model_version': 1 });

// Compound indexes for common queries
db.sensor_readings.createIndex({
    'device_id': 1,
    'sample_metadata.herb_name': 1,
    'timestamp': -1
});

db.analysis_results.createIndex({
    'device_id': 1,
    'predictions.authenticity.is_authentic': 1,
    'analysis_timestamp': -1
});

// Reports indexes
db.reports.createIndex({ 'organization_id': 1, 'created_at': -1 });
db.reports.createIndex({ 'report_type': 1, 'status': 1 });

print('Indexes created successfully');

// Create default organization
print('Creating default organization...');
const defaultOrgResult = db.organizations.insertOne({
    name: 'Default Organization',
    description: 'Default organization for initial setup',
    settings: {
        timezone: 'UTC',
        data_retention_days: 365,
        auto_analysis: true,
        notification_preferences: {
            email_alerts: true,
            quality_threshold: 0.7,
            contamination_alerts: true
        }
    },
    created_at: new Date(),
    updated_at: new Date()
});

const defaultOrgId = defaultOrgResult.insertedId;
print('Default organization created with ID: ' + defaultOrgId);

// Create default admin user
print('Creating default admin user...');
db.users.insertOne({
    username: 'admin',
    email: 'admin@ayusure.com',
    password_hash: '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/RK.PZvO.G', // password: admin123
    full_name: 'System Administrator',
    role: 'admin',
    organization_id: defaultOrgId,
    is_active: true,
    created_at: new Date(),
    updated_at: new Date(),
    login_count: 0
});

print('Default admin user created (username: admin, password: admin123)');

// Create sample device for testing
print('Creating sample device...');
db.devices.insertOne({
    device_id: 'ESP32_DEMO_001',
    device_model: 'AyuSure ESP32 v1.0',
    firmware_version: '1.0.0',
    organization_id: defaultOrgId,
    owner_user_id: db.users.findOne({ username: 'admin' })._id,
    status: 'active',
    location: {
        latitude: 28.6139,
        longitude: 77.2090,
        address: 'New Delhi, India'
    },
    description: 'Demo device for testing and development',
    calibration_data: {
        last_calibration: null,
        calibration_coefficients: {},
        next_calibration_due: null,
        calibration_history: []
    },
    statistics: {
        total_readings: 0,
        last_reading: null,
        uptime_hours: 0
    },
    created_at: new Date(),
    updated_at: new Date()
});

print('Sample device created: ESP32_DEMO_001');

// Create sample herb data for reference
print('Creating herb reference data...');
db.createCollection('herb_reference');
db.herb_reference.insertMany([
    {
        name: 'Turmeric',
        scientific_name: 'Curcuma longa',
        common_names: ['Haldi', 'Curcuma'],
        quality_parameters: {
            curcumin_content: { min: 2.0, max: 8.0, unit: 'percentage' },
            moisture_content: { max: 10.0, unit: 'percentage' },
            ash_content: { max: 9.0, unit: 'percentage' }
        },
        authenticity_markers: ['curcumin', 'demethoxycurcumin', 'bisdemethoxycurcumin'],
        common_adulterants: ['starch', 'artificial_colors', 'lead_chromate'],
        created_at: new Date()
    },
    {
        name: 'Ashwagandha',
        scientific_name: 'Withania somnifera',
        common_names: ['Winter Cherry', 'Indian Ginseng'],
        quality_parameters: {
            withanolides_content: { min: 0.3, max: 3.0, unit: 'percentage' },
            moisture_content: { max: 12.0, unit: 'percentage' },
            ash_content: { max: 10.0, unit: 'percentage' }
        },
        authenticity_markers: ['withanoside', 'withanolide_a', 'withanolide_d'],
        common_adulterants: ['other_solanaceae', 'starch', 'sand'],
        created_at: new Date()
    },
    {
        name: 'Brahmi',
        scientific_name: 'Bacopa monnieri',
        common_names: ['Water Hyssop', 'Bacopa'],
        quality_parameters: {
            bacosides_content: { min: 10.0, max: 20.0, unit: 'percentage' },
            moisture_content: { max: 10.0, unit: 'percentage' },
            ash_content: { max: 14.0, unit: 'percentage' }
        },
        authenticity_markers: ['bacoside_a', 'bacoside_b', 'brahmine'],
        common_adulterants: ['other_herbs', 'leaves', 'stems'],
        created_at: new Date()
    }
]);

print('Herb reference data created');

// Create application settings
print('Creating application settings...');
db.createCollection('app_settings');
db.app_settings.insertOne({
    _id: 'global_settings',
    ai_models: {
        authenticity_model_version: '1.0.0',
        quality_model_version: '1.0.0',
        contamination_model_version: '1.0.0',
        last_updated: new Date()
    },
    system_limits: {
        max_devices_per_org: 100,
        max_readings_per_day: 10000,
        max_report_size_mb: 50,
        data_retention_days: 365
    },
    notification_settings: {
        email_enabled: true,
        sms_enabled: false,
        webhook_enabled: true,
        alert_thresholds: {
            quality_score: 0.6,
            authenticity_confidence: 0.7,
            contamination_level: 0.3
        }
    },
    maintenance: {
        scheduled_maintenance: false,
        maintenance_message: '',
        last_backup: null,
        next_cleanup: new Date(Date.now() + 24 * 60 * 60 * 1000) // Tomorrow
    },
    created_at: new Date(),
    updated_at: new Date()
});

print('Application settings created');

print('MongoDB initialization completed successfully!');
print('');
print('Default credentials:');
print('  Username: admin');
print('  Password: admin123');
print('');
print('Sample device: ESP32_DEMO_001');
print('Organization: Default Organization');
print('');
print('IMPORTANT: Change the default admin password after first login!');