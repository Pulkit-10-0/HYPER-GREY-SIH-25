"""
CLI Commands for AyuSure Backend
Provides command-line utilities for database management and testing
"""
import click
from flask import current_app
from flask.cli import with_appcontext

from backend.utils.sample_data import SampleDataGenerator


@click.command()
@with_appcontext
def init_db():
    """Initialize database with indexes and sample data"""
    try:
        # Setup database indexes
        current_app.db.setup_indexes()
        click.echo("Database indexes created successfully")
        
        # Generate sample data
        generator = SampleDataGenerator()
        sample_data = generator.generate_all_sample_data()
        
        click.echo("Sample data generated successfully:")
        click.echo(f"- Organization: {sample_data['organization']['name']}")
        click.echo(f"- Users: {len(sample_data['users'])}")
        click.echo(f"- Devices: {len(sample_data['devices'])}")
        
        # Display sample user credentials
        click.echo("\nSample user credentials:")
        for user in sample_data['users']:
            click.echo(f"- {user['role']}: {user['username']} / {user['role'].title()}123!")
        
    except Exception as e:
        click.echo(f"Error initializing database: {str(e)}", err=True)


@click.command()
@with_appcontext
def cleanup_db():
    """Clean up sample data from database"""
    try:
        generator = SampleDataGenerator()
        generator.cleanup_sample_data()
        click.echo("Sample data cleaned up successfully")
        
    except Exception as e:
        click.echo(f"Error cleaning up database: {str(e)}", err=True)


@click.command()
@with_appcontext
def db_stats():
    """Display database statistics"""
    try:
        stats = current_app.db.get_collection_stats()
        
        click.echo("Database Statistics:")
        click.echo("-" * 40)
        
        for collection, data in stats.items():
            click.echo(f"{collection}:")
            click.echo(f"  Documents: {data['count']}")
            click.echo(f"  Size: {data['size']} bytes")
            click.echo()
        
    except Exception as e:
        click.echo(f"Error getting database stats: {str(e)}", err=True)


@click.command()
@with_appcontext
def health_check():
    """Perform system health check"""
    try:
        # Check database health
        db_health = current_app.db.health_check()
        click.echo(f"Database: {db_health['status']}")
        
        # Check Redis health
        try:
            current_app.redis.ping()
            click.echo("Redis: healthy")
        except Exception:
            click.echo("Redis: unhealthy")
        
        click.echo("Health check completed")
        
    except Exception as e:
        click.echo(f"Error performing health check: {str(e)}", err=True)


def register_commands(app):
    """Register CLI commands with Flask app"""
    app.cli.add_command(init_db)
    app.cli.add_command(cleanup_db)
    app.cli.add_command(db_stats)
    app.cli.add_command(health_check)